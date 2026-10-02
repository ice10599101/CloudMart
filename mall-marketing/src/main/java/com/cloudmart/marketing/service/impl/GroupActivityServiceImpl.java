package com.cloudmart.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.GroupActivity;
import com.cloudmart.marketing.entity.GroupMember;
import com.cloudmart.marketing.entity.GroupOrder;
import com.cloudmart.marketing.repository.GroupActivityMapper;
import com.cloudmart.marketing.repository.GroupMemberMapper;
import com.cloudmart.marketing.repository.GroupOrderMapper;
import com.cloudmart.marketing.service.GroupActivityService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拼团服务实现（T10）。
 *
 * <p>资金模式固定为"成团后建单付款"：失败团仅释放预留权益（成员 EXPIRED），
 * 不产生任何退款事实。人数与终态以 DB 为权威——开团数/组人数条件原子递增、
 * 成员 (group,user)/(activity,user) 唯一键裁决、成团 CAS 与 Outbox 同事务；
 * Redis Lua 只做流量预筛（提前拒绝重复/满员），投影可随时由 DB 重建。
 * 超时与最后一人加入的竞争由 CAS 保证只允许一个终态。</p>
 */
@Service
public class GroupActivityServiceImpl implements GroupActivityService {

    private static final Logger log = LoggerFactory.getLogger(GroupActivityServiceImpl.class);

    private static final String GROUP_KEY_PREFIX = "marketing:group:";
    private static final String GROUP_USER_SET_PREFIX = "marketing:group_users:";
    private static final String ACTIVITY_USER_SET_PREFIX = "marketing:activity_users:";
    private static final Duration GROUP_TTL = Duration.ofHours(48);

    private final GroupActivityMapper activityMapper;
    private final GroupOrderMapper groupOrderMapper;
    private final GroupMemberMapper memberMapper;
    private final MarketingConverter converter;
    private final StringRedisTemplate redisTemplate;
    private final OutboxService outboxService;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final DefaultRedisScript<List> joinGroupScript;

    public GroupActivityServiceImpl(GroupActivityMapper activityMapper,
                                    GroupOrderMapper groupOrderMapper,
                                    GroupMemberMapper memberMapper,
                                    MarketingConverter converter,
                                    StringRedisTemplate redisTemplate,
                                    OutboxService outboxService,
                                    tools.jackson.databind.ObjectMapper objectMapper) {
        this.activityMapper = activityMapper;
        this.groupOrderMapper = groupOrderMapper;
        this.memberMapper = memberMapper;
        this.converter = converter;
        this.redisTemplate = redisTemplate;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;

        // 加载 Lua 原子拼团脚本（预筛）
        this.joinGroupScript = new DefaultRedisScript<>();
        this.joinGroupScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/group_join.lua")));
        this.joinGroupScript.setResultType(List.class);
    }

    @Override
    @Transactional
    public GroupActivityDTO createActivity(CreateGroupActivityRequest request) {
        if (request.startTime().isAfter(request.endTime())) {
            throw new BusinessException("INVALID_TIME_RANGE", "开始时间不能晚于结束时间");
        }
        if (request.groupPrice().compareTo(request.originalPrice()) >= 0) {
            throw new BusinessException("INVALID_PRICE", "拼团价必须低于原价");
        }
        GroupActivity entity = converter.toEntity(request);
        activityMapper.insert(entity);
        return converter.toDTO(entity);
    }

    @Override
    @Transactional
    public GroupActivityDTO enableActivity(Long id) {
        GroupActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        if ("ENDED".equals(activity.getStatus())) {
            throw new BusinessException("ACTIVITY_ENDED", "已结束的活动不可启用");
        }
        if (LocalDateTime.now().isAfter(activity.getEndTime())) {
            activity.setStatus("ENDED");
            activityMapper.updateById(activity);
            throw new BusinessException("ACTIVITY_EXPIRED", "活动已过期");
        }
        activity.setStatus("ENABLED");
        activityMapper.updateById(activity);
        return converter.toDTO(activity);
    }

    @Override
    @Transactional
    public GroupActivityDTO disableActivity(Long id) {
        GroupActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        activity.setStatus("DISABLED");
        activityMapper.updateById(activity);
        return converter.toDTO(activity);
    }

    @Override
    public GroupActivityDTO getActivity(Long id) {
        GroupActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        return converter.toDTO(activity);
    }

    @Override
    public IPage<GroupActivityDTO> listActivities(String status, int page, int size) {
        LambdaQueryWrapper<GroupActivity> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(GroupActivity::getStatus, status);
        }
        wrapper.orderByDesc(GroupActivity::getCreatedAt);
        IPage<GroupActivity> pageResult = activityMapper.selectPage(new Page<>(page, size), wrapper);
        Page<GroupActivityDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(converter.toActivityDTOList(pageResult.getRecords()));
        return dtoPage;
    }

    @Override
    @Transactional
    @SentinelResource(value = "joinGroup", fallback = "joinGroupFallback")
    public GroupOrderDTO joinGroup(Long userId, JoinGroupRequest request) {
        GroupActivity activity = activityMapper.selectById(request.activityId());
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        if (!"ENABLED".equals(activity.getStatus())) {
            throw new BusinessException("ACTIVITY_NOT_ENABLED", "拼团活动未启用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new BusinessException("ACTIVITY_NOT_IN_PROGRESS", "拼团活动未在进行中");
        }

        GroupOrder groupOrder;
        boolean isLeader;

        if (request.groupOrderId() != null) {
            // 参团：加入已有拼团组——T10：组必须属于请求的活动（活动 A 不接受活动 B 的组）
            groupOrder = groupOrderMapper.selectById(request.groupOrderId());
            if (groupOrder == null) {
                throw new BusinessException("GROUP_NOT_FOUND", "拼团组不存在");
            }
            if (!activity.getId().equals(groupOrder.getActivityId())) {
                throw new BusinessException("GROUP_ACTIVITY_MISMATCH", "拼团组不属于该活动");
            }
            if (!"PENDING".equals(groupOrder.getStatus())) {
                throw new BusinessException("GROUP_NOT_PENDING", "拼团组已结束");
            }
            isLeader = false;
        } else {
            // 开团：开团数原子递增（0 行 = 已达最大开团数），并发开团不超卖
            if (groupOrderMapper.incrementActivityGroups(activity.getId()) == 0) {
                throw new BusinessException("MAX_GROUPS_REACHED", "已达到最大开团数");
            }
            groupOrder = new GroupOrder();
            groupOrder.setActivityId(activity.getId());
            groupOrder.setLeaderUserId(userId);
            groupOrder.setCurrentNumber(0);
            groupOrder.setTargetNumber(activity.getTargetNumber());
            groupOrder.setStatus("PENDING");
            groupOrder.setExpireTime(now.plus(GROUP_TTL));
            groupOrderMapper.insert(groupOrder);
            isLeader = true;

            // Redis 预筛投影初始化（DB 为权威，投影可重建）
            initGroupRedisState(groupOrder);
        }

        // Redis 预筛：提前拒绝明显重复/满员（DB 唯一键与 CAS 是最终裁决）
        @SuppressWarnings("unchecked")
        List<Object> luaResult = redisTemplate.execute(
                joinGroupScript,
                List.of(
                        GROUP_KEY_PREFIX + groupOrder.getId(),
                        GROUP_USER_SET_PREFIX + groupOrder.getId(),
                        ACTIVITY_USER_SET_PREFIX + activity.getId()
                ),
                userId.toString(),
                activity.getId().toString(),
                activity.getPerUserLimit().toString(),
                String.valueOf(GROUP_TTL.toSeconds())
        );

        if (luaResult == null || luaResult.isEmpty()) {
            throw new BusinessException("GROUP_JOIN_FAILED", "参团操作失败");
        }

        int resultCode = ((Number) luaResult.getFirst()).intValue();
        // -1 组已结束 / -2 已在组 / -3 已参过活动 / -4 满员：预筛拒绝，DB 事实随后可核
        if (resultCode < 0) {
            throw switch (resultCode) {
                case -1 -> new BusinessException("GROUP_NOT_PENDING", "拼团组已结束");
                case -2 -> new BusinessException("USER_ALREADY_IN_GROUP", "您已在此拼团组中");
                case -3 -> new BusinessException("USER_ALREADY_JOINED_ACTIVITY", "您已参加此活动");
                default -> new BusinessException("GROUP_FULL", "拼团组已满");
            };
        }

        // DB 权威：成员事实落库（uk(group,user) 防重复入组；uk(activity,user) 活动限购事实）
        GroupMember member = new GroupMember();
        member.setGroupOrderId(groupOrder.getId());
        member.setUserId(userId);
        member.setActivityId(activity.getId());
        member.setIsLeader(isLeader);
        member.setStatus("JOINED");
        member.setJoinedAt(now);
        try {
            memberMapper.insert(member);
        } catch (DuplicateKeyException e) {
            // 预筛投影丢失/陈旧时 DB 唯一键兜底裁决
            throw new BusinessException("USER_ALREADY_JOINED_ACTIVITY", "您已参加此活动");
        }

        // DB 权威人数原子递增（0 行 = 满员或已终态），递增后人数为准
        if (groupOrderMapper.incrementMemberCount(groupOrder.getId()) == 0) {
            throw new BusinessException("GROUP_FULL", "拼团组已满");
        }

        // 成团判定：CAS PENDING→SUCCESS（与超时扫描竞争只允许一方生效）
        GroupOrder current = groupOrderMapper.selectById(groupOrder.getId());
        if (groupOrderMapper.markSuccess(groupOrder.getId(), now) == 1) {
            log.info("[T10] 拼团成团 groupOrderId={} members={}", groupOrder.getId(), current.getCurrentNumber());

            // 成团事件与成团 CAS 同事务登记（Outbox）：业务提交则事件必然可见，
            // 重复投递由消费侧稳定订单键幂等吸收
            List<Long> memberUserIds = getGroupMemberUserIds(groupOrder.getId());
            outboxService.record(groupSuccessEvent(groupOrder.getId(), activity, memberUserIds));
            current.setStatus("SUCCESS");
            current.setSuccessTime(now);
        } else {
            current = groupOrderMapper.selectById(groupOrder.getId());
        }

        return buildGroupOrderDTO(current);
    }

    /** 成团事件（稳定 eventId：group-success-{id}，Outbox/消费侧双端幂等） */
    private EventEnvelope groupSuccessEvent(Long groupOrderId, GroupActivity activity, List<Long> memberUserIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupOrderId", groupOrderId);
        payload.put("activityId", activity.getId());
        payload.put("productId", activity.getProductId());
        payload.put("skuId", activity.getSkuId());
        payload.put("groupPrice", activity.getGroupPrice().toPlainString());
        payload.put("memberUserIds", memberUserIds);
        try {
            String json = objectMapper.writeValueAsString(payload);
            return new EventEnvelope("group-success-" + groupOrderId, "GROUP_SUCCESS", 1,
                    String.valueOf(groupOrderId), 1, System.currentTimeMillis(), null, json);
        } catch (Exception e) {
            throw new IllegalStateException("成团事件序列化失败 groupOrderId=" + groupOrderId, e);
        }
    }

    @Override
    public GroupOrderDTO getGroupOrder(Long groupOrderId) {
        GroupOrder groupOrder = groupOrderMapper.selectById(groupOrderId);
        if (groupOrder == null) {
            throw new BusinessException("GROUP_NOT_FOUND", "拼团组不存在");
        }
        return buildGroupOrderDTO(groupOrder);
    }

    @Override
    public IPage<GroupOrderDTO> listGroupOrders(Long activityId, String status, int page, int size) {
        LambdaQueryWrapper<GroupOrder> wrapper = new LambdaQueryWrapper<>();
        if (activityId != null) {
            wrapper.eq(GroupOrder::getActivityId, activityId);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(GroupOrder::getStatus, status);
        }
        wrapper.orderByDesc(GroupOrder::getCreatedAt);
        IPage<GroupOrder> pageResult = groupOrderMapper.selectPage(new Page<>(page, size), wrapper);

        Page<GroupOrderDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream().map(this::buildGroupOrderDTO).toList());
        return dtoPage;
    }

    /**
     * T10 超时处理（mall-job 定时触发）：CAS 逐组过期 + 成员释放预留权益。
     *
     * <p>与"成团后建单付款"模式一致：失败团不产生退款事实（成员标 EXPIRED
     * 而非 REFUNDED）；与最后一人加入的竞争由 CAS 裁决——成团 CAS 赢则本方法
     * 的过期 CAS 不生效，反之亦然。不再发送 group-expired MQ：payment 侧退款
     * 消费者已随旧链路删除，空退款消息只会制造误导。</p>
     */
    @Override
    @Transactional
    public void handleGroupExpiration() {
        List<GroupOrder> dueGroups = groupOrderMapper.selectList(
                new LambdaQueryWrapper<GroupOrder>()
                        .eq(GroupOrder::getStatus, "PENDING")
                        .lt(GroupOrder::getExpireTime, LocalDateTime.now())
        );
        for (GroupOrder group : dueGroups) {
            if (groupOrderMapper.markExpired(group.getId()) == 0) {
                continue; // 已成团/已被并发扫描过期：竞争败者不动终态
            }
            int members = groupOrderMapper.markMembersExpired(group.getId());
            clearGroupRedisProjection(group.getId());
            log.info("[T10] 拼团组过期并释放预留权益 groupOrderId={} members={}", group.getId(), members);
        }
        if (!dueGroups.isEmpty()) {
            log.info("[T10] 本轮扫描到期拼团组 {} 个", dueGroups.size());
        }
    }

    private void initGroupRedisState(GroupOrder groupOrder) {
        String groupKey = GROUP_KEY_PREFIX + groupOrder.getId();
        redisTemplate.opsForHash().put(groupKey, "currentNumber", "0");
        redisTemplate.opsForHash().put(groupKey, "targetNumber", groupOrder.getTargetNumber().toString());
        redisTemplate.opsForHash().put(groupKey, "status", "PENDING");
        redisTemplate.expire(groupKey, GROUP_TTL);
    }

    /** 清理 Redis 投影（尽力而为）：DB 事实为准，投影可重建 */
    private void clearGroupRedisProjection(Long groupOrderId) {
        try {
            redisTemplate.delete(GROUP_KEY_PREFIX + groupOrderId);
            redisTemplate.delete(GROUP_USER_SET_PREFIX + groupOrderId);
        } catch (Exception e) {
            log.warn("[T10] Redis 投影清理失败 groupOrderId={}: {}", groupOrderId, e.getMessage());
        }
    }

    private List<Long> getGroupMemberUserIds(Long groupOrderId) {
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupOrderId, groupOrderId)
        );
        return members.stream().map(GroupMember::getUserId).toList();
    }

    private GroupOrderDTO buildGroupOrderDTO(GroupOrder groupOrder) {
        List<GroupMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupOrderId, groupOrder.getId())
                        .orderByDesc(GroupMember::getIsLeader)
                        .orderByAsc(GroupMember::getJoinedAt)
        );
        return new GroupOrderDTO(
                groupOrder.getId(),
                groupOrder.getActivityId(),
                groupOrder.getLeaderUserId(),
                groupOrder.getCurrentNumber(),
                groupOrder.getTargetNumber(),
                groupOrder.getStatus(),
                groupOrder.getExpireTime(),
                groupOrder.getSuccessTime(),
                groupOrder.getCreatedAt(),
                converter.toMemberDTOList(members)
        );
    }

    public GroupOrderDTO joinGroupFallback(Long userId, JoinGroupRequest request, Throwable throwable) {
        log.warn("joinGroup fallback triggered, userId={}, activityId={}: {}", userId, request.activityId(), throwable.getMessage());
        throw new BusinessException("GROUP_JOIN_FAILED", "参团请求繁忙，请稍后重试");
    }
}
