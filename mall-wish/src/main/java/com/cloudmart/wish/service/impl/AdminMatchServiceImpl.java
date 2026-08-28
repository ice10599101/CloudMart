package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.MatchConfig;
import com.cloudmart.wish.entity.MatchGroup;
import com.cloudmart.wish.entity.MatchMember;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.enums.MatchGroupStatus;
import com.cloudmart.wish.enums.MatchMemberStatus;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.mq.MatchEventProducer;
import com.cloudmart.wish.repository.MatchGroupMapper;
import com.cloudmart.wish.repository.MatchMemberMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.AdminMatchService;
import com.cloudmart.wish.service.MatchConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理端同愿匹配服务实现（Sprint 2.6 管理后台）。
 *
 * <p>小组管理：全量列表（含 CLOSED）+ 最近活跃时间监控 + 异常小组强制解散
 * （与组长解散同一内部链路，成员收到通知）。
 * 算法配置：权重/阈值/限频键值编辑，实时生效。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminMatchServiceImpl implements AdminMatchService {

    private final MatchGroupMapper groupMapper;
    private final MatchMemberMapper memberMapper;
    private final WishUserStatMapper userStatMapper;
    private final MatchConfigService matchConfigService;
    private final MatchEventProducer matchEventProducer;
    private final UserFeignClient userFeignClient;

    @Override
    public List<AdminMatchGroupRow> listGroups(String status, String keyword) {
        LambdaQueryWrapper<MatchGroup> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            try {
                query.eq(MatchGroup::getStatus, MatchGroupStatus.valueOf(status.trim()));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "非法的小组状态: " + status);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            query.like(MatchGroup::getKeyword, keyword.trim());
        }
        query.orderByDesc(MatchGroup::getId);
        List<MatchGroup> groups = groupMapper.selectList(query);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<Long> groupIds = groups.stream().map(MatchGroup::getId).toList();
        List<MatchMember> members = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .in(MatchMember::getGroupId, groupIds)
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE));
        Map<Long, LocalDateTime> lastActives = members.isEmpty() ? Map.of()
                : userStatMapper.selectBatchIds(
                        members.stream().map(MatchMember::getUserId).collect(Collectors.toSet())).stream()
                        .filter(s -> s.getLastActiveAt() != null)
                        .collect(Collectors.toMap(WishUserStat::getUserId, WishUserStat::getLastActiveAt,
                                (a, b) -> a));

        // 各组最近活跃时间 = 组内成员 last_active_at 的最大值
        Map<Long, LocalDateTime> groupLatest = new java.util.HashMap<>();
        for (MatchMember member : members) {
            LocalDateTime last = lastActives.get(member.getUserId());
            if (last != null) {
                groupLatest.merge(member.getGroupId(), last,
                        (a, b) -> a.isAfter(b) ? a : b);
            }
        }

        Set<Long> leaderIds = groups.stream().map(MatchGroup::getLeaderId).collect(Collectors.toSet());
        Map<Long, String> leaderNicknames = fetchNicknames(leaderIds);

        List<AdminMatchGroupRow> rows = new ArrayList<>();
        for (MatchGroup group : groups) {
            rows.add(new AdminMatchGroupRow(group.getId(), group.getKeyword(), group.getMemberCount(),
                    group.getMaxMembers(), group.getStatus().name(), group.getCityCode(),
                    group.getLeaderId(),
                    leaderNicknames.getOrDefault(group.getLeaderId(), "心愿旅人"),
                    group.getCreatedAt(), groupLatest.get(group.getId())));
        }
        return rows;
    }

    @Override
    @Transactional
    public void forceDissolve(Long groupId, Long adminUserId) {
        MatchGroup group = groupMapper.selectById(groupId);
        if (group == null) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_NOT_FOUND, "小队不存在或已解散");
        }
        if (group.getStatus() == MatchGroupStatus.CLOSED) {
            throw new BusinessException(WishErrorCodes.WISH_GROUP_NOT_FOUND, "小队已解散");
        }

        // 与组长解散同一链路：关闭 + 成员置 LEFT + 逐成员通知
        groupMapper.update(null, new LambdaUpdateWrapper<MatchGroup>()
                .set(MatchGroup::getStatus, MatchGroupStatus.CLOSED)
                .set(MatchGroup::getClosedAt, LocalDateTime.now(ZoneId.of("UTC")))
                .set(MatchGroup::getMemberCount, 0)
                .eq(MatchGroup::getId, groupId));
        List<MatchMember> actives = memberMapper.selectList(new LambdaQueryWrapper<MatchMember>()
                .eq(MatchMember::getGroupId, groupId)
                .eq(MatchMember::getStatus, MatchMemberStatus.ACTIVE));
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        for (MatchMember member : actives) {
            member.setStatus(MatchMemberStatus.LEFT);
            member.setLeftAt(now);
            memberMapper.updateById(member);
            matchEventProducer.publishSquadEvent(member.getUserId(), groupId,
                    "小队已解散", "小组「" + group.getKeyword() + "」已被管理员解散");
        }
        log.info("管理端强制解散小组, groupId={}, members={}, adminUserId={}",
                groupId, actives.size(), adminUserId);
    }

    @Override
    public List<MatchConfig> listConfigs() {
        return matchConfigService.listConfigs();
    }

    @Override
    public MatchConfig updateConfig(String configKey, String configValue, Long adminUserId) {
        return matchConfigService.updateConfig(configKey, configValue, adminUserId);
    }

    private Map<Long, String> fetchNicknames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .filter(m -> m.get("id") instanceof Number)
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> (String) m.getOrDefault("nickname", "心愿旅人"),
                                (a, b) -> a));
            }
        } catch (Exception ex) {
            log.warn("批量获取组长昵称失败，降级占位: {}", ex.getMessage());
        }
        return Map.of();
    }
}
