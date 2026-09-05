package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishFulfillment;
import com.cloudmart.wish.entity.WishResourceLog;
import com.cloudmart.wish.entity.WishUserStat;
import com.cloudmart.wish.repository.WishFulfillmentMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishResourceLogMapper;
import com.cloudmart.wish.repository.WishUserStatMapper;
import com.cloudmart.wish.service.MaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Phase 1 运维类定时任务实现（文档 9.1，四AB 审计 P0-4）。
 *
 * <p>通用约束：游标分批 500 避免长事务；幂等（重复执行无副作用——
 * 衰减/对账/升级均以当前状态为条件写）；业务异常单条吞掉计数不中断批次。
 * evaluateLevel 复用 UserStatServiceImpl 表驱动（6.5 单一数据源）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceServiceImpl implements MaintenanceService {

    private static final int BATCH_SIZE = 500;
    private static final int STARLIGHT_FLOOR = 10;
    private static final int DECAY_AMOUNT = 2;
    private static final int RISK_DECAY = 1;

    private final WishUserStatMapper userStatMapper;
    private final WishResourceLogMapper resourceLogMapper;
    private final WishMapper wishMapper;
    private final WishFulfillmentMapper fulfillmentMapper;

    @Override
    public MapResult starlightDecay() {
        final LocalDateTime inactiveBefore = LocalDateTime.now(ZoneId.of("UTC")).minusDays(30);
        long processed = 0;
        long failed = 0;
        long lastUserId = 0;
        while (true) {
            final List<WishUserStat> batch = userStatMapper.selectList(
                    new LambdaQueryWrapper<WishUserStat>()
                            .gt(WishUserStat::getUserId, lastUserId)
                            .eq(WishUserStat::getIsRestricted, false)
                            .gt(WishUserStat::getStarlightBalance, STARLIGHT_FLOOR)
                            .lt(WishUserStat::getLastActiveAt, inactiveBefore)
                            .orderByAsc(WishUserStat::getUserId)
                            .last("LIMIT " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (final WishUserStat stat : batch) {
                lastUserId = stat.getUserId();
                try {
                    final int newBalance = Math.max(STARLIGHT_FLOOR,
                            (stat.getStarlightBalance() != null ? stat.getStarlightBalance() : 0) - DECAY_AMOUNT);
                    final int delta = newBalance - stat.getStarlightBalance();
                    if (delta == 0) {
                        continue;
                    }
                    userStatMapper.update(null, new LambdaUpdateWrapper<WishUserStat>()
                            .set(WishUserStat::getStarlightBalance, newBalance)
                            .eq(WishUserStat::getUserId, stat.getUserId())
                            .eq(WishUserStat::getStarlightBalance, stat.getStarlightBalance()));
                    final WishResourceLog logRow = new WishResourceLog();
                    logRow.setUserId(stat.getUserId());
                    logRow.setDelta(delta);
                    logRow.setType(com.cloudmart.wish.enums.ResourceLogType.SPEND);
                    logRow.setSource("DECAY");
                    logRow.setBalanceAfter(newBalance);
                    resourceLogMapper.insert(logRow);
                    processed++;
                } catch (Exception ex) {
                    failed++;
                    log.error("星光衰减失败 userId={}", stat.getUserId(), ex);
                }
            }
        }
        log.info("[wish-starlight-decay] 完成 processed={} failed={}", processed, failed);
        return new MapResult("starlightDecay", processed, failed, "余额-2 最低 " + STARLIGHT_FLOOR);
    }

    @Override
    public MapResult starlightReconcile() {
        // 以 wish_resource_log 流水求和为最终事实来源（表⑩注释），余额不一致即修正
        long processed = 0;
        long fixed = 0;
        long lastUserId = 0;
        while (true) {
            final List<WishUserStat> batch = userStatMapper.selectList(
                    new LambdaQueryWrapper<WishUserStat>()
                            .gt(WishUserStat::getUserId, lastUserId)
                            .orderByAsc(WishUserStat::getUserId)
                            .last("LIMIT " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            final List<Long> ids = batch.stream().map(WishUserStat::getUserId).toList();
            lastUserId = ids.get(ids.size() - 1);
            final Map<Long, Long> sums = resourceLogMapper.selectMaps(
                            new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<WishResourceLog>()
                                    .select("user_id", "COALESCE(SUM(delta),0) AS total")
                                    .in("user_id", ids)
                                    .groupBy("user_id"))
                    .stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("user_id")).longValue(),
                            m -> ((Number) m.get("total")).longValue()));
            for (final WishUserStat stat : batch) {
                processed++;
                final long expected = sums.getOrDefault(stat.getUserId(), 0L);
                final int actual = stat.getStarlightBalance() != null ? stat.getStarlightBalance() : 0;
                if (expected != actual) {
                    fixed++;
                    log.warn("[wish-starlight-reconcile] 余额不一致 userId={} stat={} 流水求和={} → 以流水修正",
                            stat.getUserId(), actual, expected);
                    userStatMapper.update(null, new LambdaUpdateWrapper<WishUserStat>()
                            .set(WishUserStat::getStarlightBalance, (int) expected)
                            .eq(WishUserStat::getUserId, stat.getUserId()));
                }
            }
        }
        log.info("[wish-starlight-reconcile] 完成 processed={} fixed={}", processed, fixed);
        return new MapResult("starlightReconcile", processed, fixed, "以流水求和为事实来源");
    }

    @Override
    public MapResult levelUpgrade() {
        long upgraded = 0;
        long failed = 0;
        long lastUserId = 0;
        while (true) {
            final List<WishUserStat> batch = userStatMapper.selectList(
                    new LambdaQueryWrapper<WishUserStat>()
                            .gt(WishUserStat::getUserId, lastUserId)
                            .orderByAsc(WishUserStat::getUserId)
                            .last("LIMIT " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            for (final WishUserStat stat : batch) {
                lastUserId = stat.getUserId();
                try {
                    final int earned = UserStatServiceImpl.evaluateLevel(
                            nullSafe(stat.getTotalWishes()),
                            nullSafe(stat.getTotalCheckinDays()),
                            nullSafe(stat.getTotalFulfilled()),
                            nullSafe(stat.getTotalHelped()));
                    final byte current = stat.getLevel() != null ? stat.getLevel() : 1;
                    if (earned <= current) {
                        continue;
                    }
                    // 只升不降：level/highestLevel/levelTitle 同步到 earned
                    userStatMapper.update(null, new LambdaUpdateWrapper<WishUserStat>()
                            .set(WishUserStat::getLevel, (byte) earned)
                            .set(WishUserStat::getHighestLevel, (byte) Math.max(earned,
                                    stat.getHighestLevel() != null ? stat.getHighestLevel() : 1))
                            .set(WishUserStat::getLevelTitle,
                                    UserStatServiceImpl.levelTitleOf(earned))
                            .eq(WishUserStat::getUserId, stat.getUserId())
                            .eq(WishUserStat::getLevel, current));
                    upgraded++;
                    log.info("[wish-level-upgrade] userId={} {} → {}", stat.getUserId(), current, earned);
                } catch (Exception ex) {
                    failed++;
                    log.error("等级升级失败 userId={}", stat.getUserId(), ex);
                }
            }
        }
        log.info("[wish-level-upgrade] 完成 upgraded={} failed={}", upgraded, failed);
        return new MapResult("levelUpgrade", upgraded, failed, "6.5 晋级条件表，只升不降");
    }

    @Override
    public MapResult restrictionRelease() {
        // 单条集合 UPDATE 幂等：restricted_until < NOW 且 is_restricted=1 → 解除（risk_score 不清零）
        final int released = userStatMapper.update(null, new LambdaUpdateWrapper<WishUserStat>()
                .set(WishUserStat::getIsRestricted, false)
                .eq(WishUserStat::getIsRestricted, true)
                .isNotNull(WishUserStat::getRestrictedUntil)
                .lt(WishUserStat::getRestrictedUntil, LocalDateTime.now(ZoneId.of("UTC"))));
        log.info("[wish-user-restriction-release] 完成 released={}", released);
        return new MapResult("restrictionRelease", released, 0, "risk_score 不清零");
    }

    @Override
    public MapResult riskScoreDecay() {
        // 「无新违规记录满 30 天」：驳回（REJECTED）为违规事实来源——
        // 取每用户最后一次驳回时间，无驳回或超 30 天 → risk_score -1（最低 0）
        long decayed = 0;
        long lastUserId = 0;
        final LocalDateTime violationWindow = LocalDateTime.now(ZoneId.of("UTC")).minusDays(30);
        while (true) {
            final List<WishUserStat> batch = userStatMapper.selectList(
                    new LambdaQueryWrapper<WishUserStat>()
                            .gt(WishUserStat::getUserId, lastUserId)
                            .gt(WishUserStat::getRiskScore, 0)
                            .orderByAsc(WishUserStat::getUserId)
                            .last("LIMIT " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            final List<Long> ids = batch.stream().map(WishUserStat::getUserId).toList();
            lastUserId = batch.get(batch.size() - 1).getUserId();
            final Map<Long, LocalDateTime> lastRejections = fulfillmentMapper.selectList(
                            new LambdaQueryWrapper<WishFulfillment>()
                                    .in(WishFulfillment::getUserId, ids)
                                    .eq(WishFulfillment::getAuditStatus,
                                            com.cloudmart.wish.enums.AuditStatus.REJECTED))
                    .stream()
                    .collect(Collectors.toMap(WishFulfillment::getUserId, WishFulfillment::getUpdatedAt,
                            (a, b) -> a.isAfter(b) ? a : b));
            for (final WishUserStat stat : batch) {
                final LocalDateTime lastRejection = lastRejections.get(stat.getUserId());
                if (lastRejection != null && lastRejection.isAfter(violationWindow)) {
                    continue;
                }
                userStatMapper.update(null, new LambdaUpdateWrapper<WishUserStat>()
                        .set(WishUserStat::getRiskScore, stat.getRiskScore() - RISK_DECAY)
                        .eq(WishUserStat::getUserId, stat.getUserId())
                        .eq(WishUserStat::getRiskScore, stat.getRiskScore()));
                decayed++;
            }
        }
        log.info("[wish-risk-score-decay] 完成 decayed={}", decayed);
        return new MapResult("riskScoreDecay", decayed, 0, "-1 最低 0；30 天无新驳回");
    }

    @Override
    public MapResult inactiveArchive() {
        // last_active_at < 365 天的用户：其 PRIVATE/TREE_HOLE 心愿（ACTIVE/OVERDUE/FULFILLING）→ ARCHIVED
        final LocalDateTime inactiveBefore = LocalDateTime.now(ZoneId.of("UTC")).minusDays(365);
        long archived = 0;
        long lastStatId = 0;
        while (true) {
            final List<WishUserStat> batch = userStatMapper.selectList(
                    new LambdaQueryWrapper<WishUserStat>()
                            .gt(WishUserStat::getUserId, lastStatId)
                            .lt(WishUserStat::getLastActiveAt, inactiveBefore)
                            .orderByAsc(WishUserStat::getUserId)
                            .last("LIMIT " + BATCH_SIZE));
            if (batch.isEmpty()) {
                break;
            }
            final List<Long> userIds = batch.stream().map(WishUserStat::getUserId).toList();
            lastStatId = batch.get(batch.size() - 1).getUserId();
            final int updated = wishMapper.update(null, new LambdaUpdateWrapper<Wish>()
                    .set(Wish::getStatus, com.cloudmart.wish.enums.WishStatus.ARCHIVED)
                    .in(Wish::getUserId, userIds)
                    .in(Wish::getVisibility, com.cloudmart.wish.enums.WishVisibility.PRIVATE,
                            com.cloudmart.wish.enums.WishVisibility.TREE_HOLE)
                    .in(Wish::getStatus, com.cloudmart.wish.enums.WishStatus.ACTIVE,
                            com.cloudmart.wish.enums.WishStatus.OVERDUE,
                            com.cloudmart.wish.enums.WishStatus.FULFILLING));
            archived += updated;
        }
        log.info("[wish-inactive-user-archive] 完成 archived={}（归档日志：本条日志即审计记录）", archived);
        return new MapResult("inactiveArchive", archived, 0, "PRIVATE/TREE_HOLE → ARCHIVED，PUBLIC 保留");
    }

    private static int nullSafe(final Integer value) {
        return value != null ? value : 0;
    }
}
