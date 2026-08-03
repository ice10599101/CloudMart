package com.cloudmart.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.converter.MarketingConverter;
import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.GroupActivity;
import com.cloudmart.marketing.entity.GroupMember;
import com.cloudmart.marketing.entity.GroupOrder;
import com.cloudmart.marketing.mq.MarketingMessageProducer;
import com.cloudmart.marketing.repository.GroupActivityMapper;
import com.cloudmart.marketing.repository.GroupMemberMapper;
import com.cloudmart.marketing.repository.GroupOrderMapper;
import com.cloudmart.marketing.service.GroupActivityService;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 拼团服务实现。
 * 使用 Redis Lua 脚本保证原子性参团，成团后通过 MQ 触发正式订单创建，
 * 超时未成团通过 MQ 触发退款。
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
    private final MarketingMessageProducer messageProducer;
    private final DefaultRedisScript<List> joinGroupScript;

    public GroupActivityServiceImpl(GroupActivityMapper activityMapper,
                                    GroupOrderMapper groupOrderMapper,
                                    GroupMemberMapper memberMapper,
                                    MarketingConverter converter,
                                    StringRedisTemplate redisTemplate,
                                    MarketingMessageProducer messageProducer) {
        this.activityMapper = activityMapper;
        this.groupOrderMapper = groupOrderMapper;
        this.memberMapper = memberMapper;
        this.converter = converter;
        this.redisTemplate = redisTemplate;
        this.messageProducer = messageProducer;

        // 加载 Lua 原子拼团脚本
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
            // 参团：加入已有拼团组
            groupOrder = groupOrderMapper.selectById(request.groupOrderId());
            if (groupOrder == null) {
                throw new BusinessException("GROUP_NOT_FOUND", "拼团组不存在");
            }
            isLeader = false;
        } else {
            // 开团：创建新的拼团组
            if (activity.getMaxGroups() > 0 && activity.getCurrentGroups() >= activity.getMaxGroups()) {
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

            activity.setCurrentGroups(activity.getCurrentGroups() + 1);
            activityMapper.updateById(activity);
            isLeader = true;

            // 在 Redis 中初始化拼团组状态
            initGroupRedisState(groupOrder);
        }

        // 使用 Lua 脚本原子参团
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
        String resultMsg = luaResult.size() > 1 ? luaResult.get(1).toString() : "";

        return switch (resultCode) {
            case -1 -> throw new BusinessException("GROUP_NOT_PENDING", "拼团组已结束");
            case -2 -> throw new BusinessException("USER_ALREADY_IN_GROUP", "您已在此拼团组中");
            case -3 -> throw new BusinessException("USER_ALREADY_JOINED_ACTIVITY", "您已参加此活动");
            case -4 -> throw new BusinessException("GROUP_FULL", "拼团组已满");
            default -> {
                // 参团成功，写入 DB
                GroupMember member = new GroupMember();
                member.setGroupOrderId(groupOrder.getId());
                member.setUserId(userId);
                member.setActivityId(activity.getId());
                member.setIsLeader(isLeader);
                member.setStatus("JOINED");
                member.setJoinedAt(now);
                memberMapper.insert(member);

                // 从 Redis 读取最新人数更新 DB
                String currentNumStr = redisTemplate.opsForHash()
                        .get(GROUP_KEY_PREFIX + groupOrder.getId(), "currentNumber")
                        .toString();
                groupOrder.setCurrentNumber(Integer.parseInt(currentNumStr));

                boolean isGroupSuccess = (resultCode == 1);
                if (isGroupSuccess) {
                    groupOrder.setStatus("SUCCESS");
                    groupOrder.setSuccessTime(now);
                    log.info("Group order {} reached target via Lua, triggering MQ", groupOrder.getId());

                    // 成团发送 MQ 创建正式订单
                    List<Long> memberUserIds = getGroupMemberUserIds(groupOrder.getId());
                    messageProducer.sendGroupSuccessMessage(
                            groupOrder.getId(), activity.getId(),
                            activity.getProductId(), activity.getSkuId(),
                            memberUserIds
                    );
                }
                groupOrderMapper.updateById(groupOrder);

                yield buildGroupOrderDTO(groupOrder);
            }
        };
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

    @Override
    @Transactional
    public void handleGroupExpiration() {
        List<GroupOrder> expiredGroups = groupOrderMapper.selectList(
                new LambdaQueryWrapper<GroupOrder>()
                        .eq(GroupOrder::getStatus, "PENDING")
                        .lt(GroupOrder::getExpireTime, LocalDateTime.now())
        );

        for (GroupOrder group : expiredGroups) {
            List<GroupMember> members = memberMapper.selectList(
                    new LambdaQueryWrapper<GroupMember>()
                            .eq(GroupMember::getGroupOrderId, group.getId())
                            .eq(GroupMember::getStatus, "JOINED")
            );

            List<Long> memberUserIds = members.stream().map(GroupMember::getUserId).toList();

            // 通过 MQ 发送退款消息，由 payment 服务执行实际退款
            messageProducer.sendGroupExpiredMessage(group.getId(), group.getActivityId(), memberUserIds);

            // 标记 DB 状态
            group.setStatus("EXPIRED");
            groupOrderMapper.updateById(group);

            for (GroupMember member : members) {
                member.setStatus("REFUNDED");
                memberMapper.updateById(member);
            }

            log.info("Group order {} expired, {} members queued for refund via MQ",
                    group.getId(), members.size());
        }

        if (!expiredGroups.isEmpty()) {
            log.info("Processed {} expired group orders", expiredGroups.size());
        }
    }

    private void initGroupRedisState(GroupOrder groupOrder) {
        String groupKey = GROUP_KEY_PREFIX + groupOrder.getId();
        redisTemplate.opsForHash().put(groupKey, "currentNumber", "0");
        redisTemplate.opsForHash().put(groupKey, "targetNumber", groupOrder.getTargetNumber().toString());
        redisTemplate.opsForHash().put(groupKey, "status", "PENDING");
        redisTemplate.expire(groupKey, GROUP_TTL);
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
        return null;
    }
}
