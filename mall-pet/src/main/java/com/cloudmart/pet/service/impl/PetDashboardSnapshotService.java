package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetDashboardDailySnapshot;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetDashboardDailySnapshotMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 看板每日快照服务（P2-3）：趋势类指标按 UTC 自然日聚合落表，调度器每小时
 * 增量 upsert 当日值（历史日自然冻结）；看板读"快照(历史) + 实时(当日)"合并。
 *
 * <p>指标键与看板趋势字段一一对应：{@code new_pets/active_pets/activities/
 * wall_messages/visits/battles}。聚合口径与 AdminPetDashboardController 的实时
 * 路径保持一致（同一套 WHERE 条件），避免快照与实时两套数字。</p>
 *
 * <p>降级：快照写入/读取失败仅 WARN（Fail-Open），看板自动回落全实时聚合路径。</p>
 */
@Service
@Slf4j
public class PetDashboardSnapshotService {

    /** 趋势指标键（与看板 TrendPoint 字段对应） */
    public static final List<String> METRIC_KEYS =
            List.of("new_pets", "active_pets", "activities", "wall_messages", "visits", "battles");

    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetBattleMapper battleMapper;
    private final PetWallMessageMapper wallMessageMapper;
    private final PetDashboardDailySnapshotMapper snapshotMapper;
    private final JdbcTemplate jdbcTemplate;

    public PetDashboardSnapshotService(PetMapper petMapper,
                                       PetActivityMapper activityMapper,
                                       PetBattleMapper battleMapper,
                                       PetWallMessageMapper wallMessageMapper,
                                       PetDashboardDailySnapshotMapper snapshotMapper,
                                       JdbcTemplate jdbcTemplate) {
        this.petMapper = petMapper;
        this.activityMapper = activityMapper;
        this.battleMapper = battleMapper;
        this.wallMessageMapper = wallMessageMapper;
        this.snapshotMapper = snapshotMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 计算某日（UTC）的六项趋势指标 */
    private Map<String, Long> computeMetrics(LocalDate date) {
        java.time.LocalDateTime start = date.atStartOfDay();
        java.time.LocalDateTime end = date.plusDays(1).atStartOfDay();
        Map<String, Long> metrics = new HashMap<>();
        metrics.put("new_pets", count(petMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.cloudmart.pet.entity.Pet>()
                        .select("COUNT(*) AS c").ge("created_at", start).lt("created_at", end))));
        metrics.put("active_pets", count(activityMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PetActivity>()
                        .select("COUNT(DISTINCT pet_id) AS c").ge("created_at", start).lt("created_at", end))));
        metrics.put("activities", count(activityMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PetActivity>()
                        .select("COUNT(*) AS c").ge("created_at", start).lt("created_at", end))));
        metrics.put("wall_messages", count(wallMessageMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PetWallMessage>()
                        .select("COUNT(*) AS c").ge("created_at", start).lt("created_at", end))));
        metrics.put("visits", count(activityMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PetActivity>()
                        .select("COUNT(*) AS c").eq("activity_type", "VISIT")
                        .ge("created_at", start).lt("created_at", end))));
        metrics.put("battles", count(battleMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PetBattle>()
                        .select("COUNT(*) AS c").ge("created_at", start).lt("created_at", end))));
        return metrics;
    }

    /** 调度器每小时调用：upsert 当日快照（uk 冲突时更新值，历史日不受影响） */
    public void writeTodaySnapshot() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        try {
            Map<String, Long> metrics = computeMetrics(today);
            for (Map.Entry<String, Long> entry : metrics.entrySet()) {
                jdbcTemplate.update("""
                                INSERT INTO pet_dashboard_daily_snapshot
                                    (id, stat_date, metric_key, metric_value)
                                VALUES (?, ?, ?, ?) AS new
                                ON DUPLICATE KEY UPDATE metric_value = new.metric_value
                                """,
                        com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
                        Date.valueOf(today), entry.getKey(), entry.getValue());
            }
            log.debug("看板当日快照已写入: date={}, metrics={}", today, metrics);
        } catch (Exception e) {
            log.warn("看板当日快照写入失败（Fail-Open，看板回落实时聚合）: date={}", today, e);
        }
    }

    /**
     * 读取 [from, to] 区间的历史日快照：date → (metricKey → value)。
     * 读取失败返回空 map（调用方回落实时聚合）。
     */
    public Map<LocalDate, Map<String, Long>> snapshotsBetween(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return Map.of();
        }
        try {
            Map<LocalDate, Map<String, Long>> result = new HashMap<>();
            for (PetDashboardDailySnapshot row : snapshotMapper.selectList(
                    new LambdaQueryWrapper<PetDashboardDailySnapshot>()
                            .ge(PetDashboardDailySnapshot::getStatDate, from)
                            .le(PetDashboardDailySnapshot::getStatDate, to))) {
                result.computeIfAbsent(row.getStatDate(), key -> new HashMap<>())
                        .put(row.getMetricKey(), row.getMetricValue() != null ? row.getMetricValue() : 0);
            }
            return result;
        } catch (Exception e) {
            log.warn("看板快照读取失败（回落实时聚合）: from={}, to={}", from, to, e);
            return Map.of();
        }
    }

    /** 区间内每日六项指标是否全部有快照（决定看板是否可走快照路径） */
    public boolean fullyCovered(Map<LocalDate, Map<String, Long>> snapshots, LocalDate from, LocalDate to) {
        Set<LocalDate> expected = new HashSet<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            expected.add(date);
        }
        if (!snapshots.keySet().containsAll(expected)) {
            return false;
        }
        return snapshots.values().stream().allMatch(keys -> keys.keySet().containsAll(METRIC_KEYS));
    }

    private long count(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).get("c") == null) {
            return 0;
        }
        Object value = rows.get(0).get("c");
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
