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
    private final WishOperationExecutor operationExecutor;

    /** 平台报表日界（任务书 4.3：后台报表默认 Asia/Shanghai） */
    private static final java.time.ZoneId PLATFORM_ZONE = java.time.ZoneId.of("Asia/Shanghai");

    @Override
    public MapResult starlightDecay() {
        // B06：衰减转为钱包标准操作 DECAY:{userId}:{platformDate}——
        // 逐用户独立短事务，条件 UPDATE 复核不活跃与余额下限，affected=1 才写流水；
        // 唯一业务键经 wish_operation 兜底：同日重复调度/多实例执行重放原结果，不重复扣。
        final LocalDateTime inactiveBefore = LocalDateTime.now(ZoneId.of("UTC")).minusDays(30);
        final String platformDate = java.time.LocalDate.now(PLATFORM_ZONE).toString();
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
                    Boolean decayed = operationExecutor.execute("JOB", 0L, "mall-job",
                            "STARLIGHT_DECAY", stat.getUserId() + ":" + platformDate,
                            java.util.Map.of("userId", stat.getUserId(), "date", platformDate),
                            Boolean.class,
                            () -> decayOneUser(stat.getUserId(), inactiveBefore));
                    if (Boolean.TRUE.equals(decayed)) {
                        processed++;
                    }
                } catch (Exception ex) {
                    failed++;
                    log.error("星光衰减失败 userId={}", stat.getUserId(), ex);
                }
            }
        }
        log.info("[wish-starlight-decay] 完成 processed={} failed={}", processed, failed);
        return new MapResult("starlightDecay", processed, failed, "余额-2 最低 " + STARLIGHT_FLOOR + "；唯一键 DECAY:userId:date");
    }

    /**
     * 单用户衰减（调用方短事务内）：条件 UPDATE 复核"仍不活跃且余额高于下限"，
     * affected=1 才写 DECAY 流水；affected=0 表示并发已变化，静默跳过（不产生假流水）。
     */
    private Boolean decayOneUser(Long userId, LocalDateTime inactiveBefore) {
        final int affected = userStatMapper.update(null, new LambdaUpdateWrapper<WishUserStat>()
                .setSql("starlight_balance = starlight_balance - " + DECAY_AMOUNT)
                .eq(WishUserStat::getUserId, userId)
                .gt(WishUserStat::getStarlightBalance, STARLIGHT_FLOOR)
                .lt(WishUserStat::getLastActiveAt, inactiveBefore));
        if (affected == 0) {
            return Boolean.FALSE;
        }
        final int balanceAfter = userStatMapper.selectById(userId).getStarlightBalance();
        final WishResourceLog logRow = new WishResourceLog();
        logRow.setUserId(userId);
        logRow.setDelta(-DECAY_AMOUNT);
        logRow.setType(com.cloudmart.wish.enums.ResourceLogType.SPEND);
        logRow.setSource("DECAY");
        logRow.setBalanceAfter(balanceAfter);
        resourceLogMapper.insert(logRow);
        return Boolean.TRUE;
    }

    @Override
    public MapResult starlightReconcile() {
        // B06：对账默认只产生差异工单，不直接改余额——
        // 历史缺流水账户不能盲目归零（初始余额须以受审计的 opening entry 表示）；
        // 修复须走专属权限 + 原因 + 修复操作的工单流程（N02），不修改既有流水。
        long checked = 0;
        long differences = 0;
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
                checked++;
                final long flowSum = sums.getOrDefault(stat.getUserId(), 0L);
                final int balance = stat.getStarlightBalance() != null ? stat.getStarlightBalance() : 0;
                if (flowSum != balance) {
                    differences++;
                    // 差异工单（日志承载，只列 ID 与金额差；修复走 N02 人工工单）
                    log.warn("[wish-starlight-reconcile] 差异工单: userId={} 余额={} 流水求和={} 差额={}",
                            stat.getUserId(), balance, flowSum, balance - flowSum);
                }
            }
        }
        log.info("[wish-starlight-reconcile] 完成 checked={} differences={}（CHECK_ONLY：不直接改余额）",
                checked, differences);
        return new MapResult("starlightReconcile", checked, differences, "CHECK_ONLY：差异进入工单，不改余额");
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
