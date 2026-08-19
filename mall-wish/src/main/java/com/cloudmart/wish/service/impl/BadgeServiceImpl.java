package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.wish.entity.WishBadge;
import com.cloudmart.wish.entity.WishUserBadge;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.repository.WishBadgeMapper;
import com.cloudmart.wish.repository.WishUserBadgeMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.BadgeService;
import com.cloudmart.wish.service.impl.BadgeConditionParser.BadgeCondition;
import com.cloudmart.wish.vo.BadgeDefinitionVO;
import com.cloudmart.wish.vo.BadgeWallItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 徽章服务实现。
 *
 * <p>徽章定义为低频变更配置数据（个位数行），直查 DB（PK 全表极小，
 * 无缓存必要；管理端 CRUD 上线后再评估缓存）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeServiceImpl implements BadgeService {

    /** 补偿扫描游标分批大小 */
    private static final int COMPENSATION_BATCH_SIZE = 500;

    private final WishBadgeMapper wishBadgeMapper;
    private final WishUserBadgeMapper wishUserBadgeMapper;
    private final WishUserStatMapper wishUserStatMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<WishBadge> evaluateAndAward(Long userId) {
        WishUserStat stat = wishUserStatMapper.selectById(userId);
        if (stat == null) {
            return List.of();
        }
        List<WishBadge> definitions = listBadgeDefinitions();
        if (definitions.isEmpty()) {
            return List.of();
        }
        Set<Long> earnedBadgeIds = listEarnedBadgeIds(userId);

        List<WishBadge> newlyAwarded = new ArrayList<>();
        for (WishBadge badge : definitions) {
            if (earnedBadgeIds.contains(badge.getId())) {
                continue;
            }
            BadgeCondition condition = BadgeConditionParser.parse(badge.getCondition());
            if (condition == null) {
                log.warn("徽章 condition 非法，跳过判定, badgeId={}, code={}", badge.getId(), badge.getCode());
                continue;
            }
            if (condition.type().extractMetric(stat) >= condition.threshold()) {
                if (awardOnce(userId, badge)) {
                    newlyAwarded.add(badge);
                }
            }
        }
        if (!newlyAwarded.isEmpty()) {
            log.info("徽章授予, userId={}, badges={}", userId,
                    newlyAwarded.stream().map(WishBadge::getCode).toList());
        }
        return newlyAwarded;
    }

    @Override
    public List<BadgeWallItemVO> getBadgeWall(Long userId) {
        List<WishBadge> definitions = listBadgeDefinitions();
        if (definitions.isEmpty()) {
            return List.of();
        }
        WishUserStat stat = wishUserStatMapper.selectById(userId);
        Map<Long, WishUserBadge> earnedBadges = wishUserBadgeMapper.selectList(
                        new LambdaQueryWrapper<WishUserBadge>().eq(WishUserBadge::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(WishUserBadge::getBadgeId, ub -> ub));

        List<BadgeWallItemVO> wall = definitions.stream()
                .map(badge -> buildWallItem(badge, stat, earnedBadges.get(badge.getId())))
                .toList();
        // 已获得在前（获得时间倒序，最新点亮优先），未获得按 badgeId 升序（图鉴稳定顺序）
        List<BadgeWallItemVO> earnedItems = wall.stream()
                .filter(item -> Boolean.TRUE.equals(item.getEarned()))
                .sorted(Comparator.comparing(BadgeWallItemVO::getEarnedAt).reversed())
                .toList();
        List<BadgeWallItemVO> lockedItems = wall.stream()
                .filter(item -> !Boolean.TRUE.equals(item.getEarned()))
                .sorted(Comparator.comparing(BadgeWallItemVO::getBadgeId))
                .toList();
        List<BadgeWallItemVO> result = new ArrayList<>(earnedItems.size() + lockedItems.size());
        result.addAll(earnedItems);
        result.addAll(lockedItems);
        return result;
    }

    @Override
    public List<BadgeDefinitionVO> getDefinitions() {
        return listBadgeDefinitions().stream()
                .map(BadgeServiceImpl::toDefinitionVO)
                .toList();
    }

    @Override
    public CompensationResult compensationScan() {
        int scannedUsers = 0;
        int awardedBadges = 0;
        long lastUserId = 0L;
        while (true) {
            // 游标分批（userId 即主键）：避免 OFFSET 深分页，批间释放内存
            List<Long> batchUserIds = wishUserStatMapper.selectList(
                            new LambdaQueryWrapper<WishUserStat>()
                                    .select(WishUserStat::getUserId)
                                    .gt(WishUserStat::getUserId, lastUserId)
                                    .orderByAsc(WishUserStat::getUserId)
                                    .last("LIMIT " + COMPENSATION_BATCH_SIZE))
                    .stream()
                    .map(WishUserStat::getUserId)
                    .toList();
            if (batchUserIds.isEmpty()) {
                break;
            }
            for (Long userId : batchUserIds) {
                lastUserId = userId;
                scannedUsers++;
                try {
                    awardedBadges += evaluateAndAward(userId).size();
                } catch (Exception e) {
                    // 单用户异常不中断全量扫描（授权幂等，下轮扫描自动重试该用户）
                    log.warn("徽章补偿扫描单用户失败, userId={}: {}", userId, e.getMessage());
                }
            }
            if (batchUserIds.size() < COMPENSATION_BATCH_SIZE) {
                break;
            }
        }
        if (awardedBadges > 0) {
            log.info("徽章补偿扫描完成, scannedUsers={}, awardedBadges={}", scannedUsers, awardedBadges);
        }
        return new CompensationResult(scannedUsers, awardedBadges);
    }

    /**
     * 授予单枚徽章；唯一索引冲突（并发重复授予）返回 false 不视为错误。
     */
    private boolean awardOnce(Long userId, WishBadge badge) {
        try {
            WishUserBadge userBadge = new WishUserBadge();
            userBadge.setUserId(userId);
            userBadge.setBadgeId(badge.getId());
            wishUserBadgeMapper.insert(userBadge);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private BadgeWallItemVO buildWallItem(WishBadge badge, WishUserStat stat, WishUserBadge earned) {
        BadgeCondition condition = BadgeConditionParser.parse(badge.getCondition());
        BadgeDefinitionVO.ConditionVO conditionVO = condition != null
                ? new BadgeDefinitionVO.ConditionVO(
                        condition.type().name(), condition.threshold(), condition.description())
                : null;

        BadgeWallItemVO item = new BadgeWallItemVO();
        item.setBadgeId(badge.getId());
        item.setCode(badge.getCode());
        item.setName(badge.getName());
        item.setIcon(badge.getIcon() != null ? badge.getIcon() : "");
        item.setRarity(badge.getRarity() != null ? badge.getRarity() : "COMMON");
        item.setCondition(conditionVO);
        item.setDescription(condition != null ? condition.description() : badge.getName());

        boolean isEarned = earned != null;
        item.setEarned(isEarned);
        item.setEarnedAt(isEarned ? earned.getCreatedAt() : null);
        if (condition != null) {
            int current = isEarned
                    ? condition.threshold()
                    : (stat != null ? condition.type().extractMetric(stat) : 0);
            int percentage = Math.min(100, (int) Math.ceil(current * 100.0 / condition.threshold()));
            item.setProgress(new BadgeWallItemVO.ProgressVO(current, condition.threshold(), percentage));
        }
        return item;
    }

    private static BadgeDefinitionVO toDefinitionVO(WishBadge badge) {
        BadgeCondition condition = BadgeConditionParser.parse(badge.getCondition());
        BadgeDefinitionVO vo = new BadgeDefinitionVO();
        vo.setBadgeId(badge.getId());
        vo.setCode(badge.getCode());
        vo.setName(badge.getName());
        vo.setIcon(badge.getIcon() != null ? badge.getIcon() : "");
        vo.setRarity(badge.getRarity() != null ? badge.getRarity() : "COMMON");
        if (condition != null) {
            vo.setDescription(condition.description());
            vo.setCondition(new BadgeDefinitionVO.ConditionVO(
                    condition.type().name(), condition.threshold(), condition.description()));
        } else {
            vo.setDescription(badge.getName());
        }
        return vo;
    }

    private List<WishBadge> listBadgeDefinitions() {
        // 仅上架徽章参与判定与展示（V6 is_active；下架=运营撤回，重新上架自动恢复）
        return wishBadgeMapper.selectList(
                new LambdaQueryWrapper<WishBadge>()
                        .eq(WishBadge::getIsActive, true)
                        .orderByAsc(WishBadge::getId));
    }

    private Set<Long> listEarnedBadgeIds(Long userId) {
        return wishUserBadgeMapper.selectList(
                        new LambdaQueryWrapper<WishUserBadge>().eq(WishUserBadge::getUserId, userId))
                .stream()
                .map(WishUserBadge::getBadgeId)
                .collect(Collectors.toSet());
    }
}
