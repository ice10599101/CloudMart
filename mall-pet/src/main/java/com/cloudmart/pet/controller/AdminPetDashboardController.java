package com.cloudmart.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetBattle;
import com.cloudmart.pet.entity.PetBottleRecord;
import com.cloudmart.pet.entity.PetDailyQuest;
import com.cloudmart.pet.entity.PetFriend;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetRelation;
import com.cloudmart.pet.entity.PetRoom;
import com.cloudmart.pet.entity.PetRoomItem;
import com.cloudmart.pet.entity.PetWallMessage;
import com.cloudmart.pet.enums.PetFriendStatus;
import com.cloudmart.pet.enums.PetQuestStatus;
import com.cloudmart.pet.enums.PetRelationStatus;
import com.cloudmart.pet.entity.PetChatMessage;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetBattleMapper;
import com.cloudmart.pet.repository.PetChatMessageMapper;
import com.cloudmart.pet.repository.PetBottleRecordMapper;
import com.cloudmart.pet.repository.PetDailyQuestMapper;
import com.cloudmart.pet.repository.PetFriendMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetRelationMapper;
import com.cloudmart.pet.repository.PetRoomItemMapper;
import com.cloudmart.pet.repository.PetRoomMapper;
import com.cloudmart.pet.repository.PetWallMessageMapper;
import com.cloudmart.pet.vo.PetDashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 宠物数据看板管理端接口（三期）。
 *
 * <p>所有指标由 SQL 聚合实时计算（不建统计表，避免双写漂移）：规模与活跃、
 * 玩法参与（对战/捞瓶/串门/留言）、社交（关系/好友/留言墙）、家园（房间/舒适度/家具）、
 * 每日任务完成率、职业就业人数、物品购买结构。</p>
 */
@RestController
@RequestMapping("/admin/pet/dashboard")
@Tag(name = "宠物数据看板", description = "宠物模块运营指标")
@RequiredArgsConstructor
public class AdminPetDashboardController {

    private final PetMapper petMapper;
    private final PetActivityMapper activityMapper;
    private final PetBattleMapper battleMapper;
    private final PetBottleRecordMapper bottleRecordMapper;
    private final PetWallMessageMapper wallMessageMapper;
    private final PetRelationMapper relationMapper;
    private final PetFriendMapper friendMapper;
    private final PetRoomMapper roomMapper;
    private final PetRoomItemMapper roomItemMapper;
    private final PetDailyQuestMapper dailyQuestMapper;
    private final PetInventoryMapper inventoryMapper;
    private final PetChatMessageMapper chatMessageMapper;
    private final com.cloudmart.pet.config.PetMetrics metrics;
    private final com.cloudmart.pet.service.impl.PetDashboardSnapshotService snapshotService;

    @GetMapping
    @Operation(summary = "宠物看板", description = "概览 + 近 N 日趋势 + 分布排行（days 默认 14，最多 60）")
    @PreAuthorize("hasRole('INTERNAL')")
    public ApiResponse<PetDashboardVO> dashboard(
            @RequestParam(value = "days", defaultValue = "14") Integer days) {
        int safeDays = Math.min(Math.max(1, days != null ? days : 14), 60);
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekStart = today.minusDays(6).atStartOfDay();
        LocalDateTime trendStart = today.minusDays(safeDays - 1L).atStartOfDay();

        PetDashboardVO.Overview overview = new PetDashboardVO.Overview(
                petMapper.selectCount(null),
                petMapper.selectCount(new QueryWrapper<Pet>().ge("created_at", todayStart)),
                petMapper.selectCount(new QueryWrapper<Pet>().ge("created_at", weekStart)),
                distinctActivePets(todayStart),
                distinctActivePets(weekStart),
                battleMapper.selectCount(null),
                battleMapper.selectCount(new QueryWrapper<PetBattle>().ge("created_at", todayStart)),
                bottleRecordMapper.selectCount(null),
                bottleRecordMapper.selectCount(new QueryWrapper<PetBottleRecord>().ge("created_at", todayStart)),
                countActivityType("VISIT"),
                friendVisitTotal(),
                wallMessageMapper.selectCount(null),
                wallMessageMapper.selectCount(new QueryWrapper<PetWallMessage>().ge("created_at", todayStart)),
                relationMapper.selectCount(new QueryWrapper<PetRelation>()
                        .eq("status", PetRelationStatus.ACTIVE.name())),
                friendMapper.selectCount(new QueryWrapper<PetFriend>()
                        .eq("status", PetFriendStatus.ACTIVE.name())),
                roomMapper.selectCount(null),
                averageComfort(),
                dailyQuestMapper.selectCount(new QueryWrapper<PetDailyQuest>()
                        .eq("status", PetQuestStatus.CLAIMED.name())),
                countTodayQuests(),
                petMapper.selectCount(new QueryWrapper<Pet>().isNotNull("career_code")),
                purchasesByType());

        return ApiResponse.ok(new PetDashboardVO(overview,
                trend(trendStart, today, safeDays), distribution(), aiUsage(todayStart)));
    }

    // ---------------- AI 用量（P1-8 成本可观测） ----------------

    /** 今日 AI 调用次数 / 估算 token（pet_chat_message 聚合）+ 降级次数（metrics 计数器） */
    private PetDashboardVO.AiUsage aiUsage(LocalDateTime todayStart) {
        List<Map<String, Object>> rows = chatMessageMapper.selectMaps(new QueryWrapper<PetChatMessage>()
                .select("COALESCE(SUM(CASE WHEN is_ai_reply = 1 THEN 1 ELSE 0 END), 0) AS ai_replies",
                        "COALESCE(SUM(token_count), 0) AS tokens")
                .ge("created_at", todayStart));
        long aiReplies = scalar(rows, "ai_replies");
        long tokens = scalar(rows, "tokens");
        long fallback = (long) metrics.value("pet_chat_ai_fallback_total");
        return new PetDashboardVO.AiUsage(aiReplies, tokens, fallback);
    }

    // ---------------- 概览辅助 ----------------

    /** 活跃宠物 = 当日/区间内有行为流水的去重宠物数 */
    private long distinctActivePets(LocalDateTime since) {
        List<Map<String, Object>> rows = activityMapper.selectMaps(new QueryWrapper<PetActivity>()
                .select("COUNT(DISTINCT pet_id) AS c")
                .ge("created_at", since));
        return scalar(rows, "c");
    }

    private long countActivityType(String type) {
        return activityMapper.selectCount(new QueryWrapper<PetActivity>().eq("activity_type", type));
    }

    /** 好友互访次数（好友行单向统计，避免双倍） */
    private long friendVisitTotal() {
        List<Map<String, Object>> rows = friendMapper.selectMaps(new QueryWrapper<PetFriend>()
                .select("COALESCE(SUM(visit_count), 0) AS c"));
        return scalar(rows, "c");
    }

    private long averageComfort() {
        List<Map<String, Object>> rows = roomMapper.selectMaps(new QueryWrapper<PetRoom>()
                .select("COALESCE(ROUND(AVG(comfort)), 0) AS c"));
        return scalar(rows, "c");
    }

    private long countTodayQuests() {
        return dailyQuestMapper.selectCount(new QueryWrapper<PetDailyQuest>()
                .eq("quest_date", LocalDate.now(ZoneId.of("UTC"))));
    }

    private Map<String, Long> purchasesByType() {
        List<Map<String, Object>> rows = inventoryMapper.selectMaps(new QueryWrapper<PetInventory>()
                .select("item_type AS name", "COUNT(*) AS c")
                .groupBy("item_type"));
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("name")), asLong(row.get("c")));
        }
        return result;
    }

    // ---------------- 趋势 ----------------

    private List<PetDashboardVO.TrendPoint> trend(LocalDateTime since, LocalDate today, int days) {
        LocalDate fromDate = today.minusDays(days - 1L);
        LocalDate yesterday = today.minusDays(1);
        // P2-3：历史日优先读每日快照（调度器小时级增量写入）；覆盖不全（刚上线/快照故障）回落实时聚合
        Map<java.time.LocalDate, Map<String, Long>> snapshots =
                snapshotService.snapshotsBetween(fromDate, yesterday);
        if (snapshotService.fullyCovered(snapshots, fromDate, yesterday)) {
            return trendFromSnapshot(snapshots, since, today, days);
        }
        return trendRealtime(since, today, days);
    }

    /** 快照路径：历史日直读快照，当日实时聚合（扫描量只与今日数据相关，与历史总量无关） */
    private List<PetDashboardVO.TrendPoint> trendFromSnapshot(Map<java.time.LocalDate, Map<String, Long>> snapshots,
                                                              LocalDateTime since, LocalDate today, int days) {
        LocalDateTime todayStart = since.isAfter(today.atStartOfDay()) ? since : today.atStartOfDay();
        Map<String, Long> todayNewPets = toDateMap(petMapper.selectMaps(new QueryWrapper<Pet>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c").ge("created_at", todayStart)
                .groupBy("DATE(created_at)")));
        Map<String, Long> todayActivePets = groupDistinctPetsByDate(todayStart);
        Map<String, Long> todayActivities = toDateMap(activityMapper.selectMaps(new QueryWrapper<PetActivity>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c").ge("created_at", todayStart)
                .groupBy("DATE(created_at)")));
        Map<String, Long> todayWallMessages = toDateMap(wallMessageMapper.selectMaps(new QueryWrapper<PetWallMessage>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c").ge("created_at", todayStart)
                .groupBy("DATE(created_at)")));
        Map<String, Long> todayVisits = groupByDateAndType(activityMapper, "VISIT", todayStart);
        Map<String, Long> todayBattles = toDateMap(battleMapper.selectMaps(new QueryWrapper<PetBattle>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c").ge("created_at", todayStart)
                .groupBy("DATE(created_at)")));
        List<PetDashboardVO.TrendPoint> points = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            java.time.LocalDate date = today.minusDays(i);
            if (!date.isBefore(today)) {
                points.add(new PetDashboardVO.TrendPoint(date.toString(),
                        todayNewPets.getOrDefault(date.toString(), 0L),
                        todayActivePets.getOrDefault(date.toString(), 0L),
                        todayActivities.getOrDefault(date.toString(), 0L),
                        todayWallMessages.getOrDefault(date.toString(), 0L),
                        todayVisits.getOrDefault(date.toString(), 0L),
                        todayBattles.getOrDefault(date.toString(), 0L)));
            } else {
                Map<String, Long> day = snapshots.get(date);
                points.add(new PetDashboardVO.TrendPoint(date.toString(),
                        day.getOrDefault("new_pets", 0L),
                        day.getOrDefault("active_pets", 0L),
                        day.getOrDefault("activities", 0L),
                        day.getOrDefault("wall_messages", 0L),
                        day.getOrDefault("visits", 0L),
                        day.getOrDefault("battles", 0L)));
            }
        }
        return points;
    }

    /** 实时聚合路径（快照未覆盖时）：整段范围 GROUP BY，与 P2-3 改造前口径一致 */
    private List<PetDashboardVO.TrendPoint> trendRealtime(LocalDateTime since, LocalDate today, int days) {
        Map<String, Long> newPets = toDateMap(petMapper.selectMaps(new QueryWrapper<Pet>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .ge("created_at", since)
                .groupBy("DATE(created_at)")));
        Map<String, Long> activePets = groupDistinctPetsByDate(since);
        Map<String, Long> activities = toDateMap(activityMapper.selectMaps(new QueryWrapper<PetActivity>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .ge("created_at", since)
                .groupBy("DATE(created_at)")));
        Map<String, Long> wallMessages = toDateMap(wallMessageMapper.selectMaps(new QueryWrapper<PetWallMessage>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .ge("created_at", since)
                .groupBy("DATE(created_at)")));
        Map<String, Long> visits = groupByDateAndType(activityMapper, "VISIT", since);
        Map<String, Long> battles = toDateMap(battleMapper.selectMaps(new QueryWrapper<PetBattle>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .ge("created_at", since)
                .groupBy("DATE(created_at)")));
        List<PetDashboardVO.TrendPoint> points = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            points.add(new PetDashboardVO.TrendPoint(date,
                    newPets.getOrDefault(date, 0L),
                    activePets.getOrDefault(date, 0L),
                    activities.getOrDefault(date, 0L),
                    wallMessages.getOrDefault(date, 0L),
                    visits.getOrDefault(date, 0L),
                    battles.getOrDefault(date, 0L)));
        }
        return points;
    }

    private Map<String, Long> groupByDateAndType(PetActivityMapper mapper, String type, LocalDateTime since) {
        return toDateMap(mapper.selectMaps(new QueryWrapper<PetActivity>()
                .select("DATE(created_at) AS d", "COUNT(*) AS c")
                .eq("activity_type", type)
                .ge("created_at", since)
                .groupBy("DATE(created_at)")));
    }

    private Map<String, Long> groupDistinctPetsByDate(LocalDateTime since) {
        return toDateMap(activityMapper.selectMaps(new QueryWrapper<PetActivity>()
                .select("DATE(created_at) AS d", "COUNT(DISTINCT pet_id) AS c")
                .ge("created_at", since)
                .groupBy("DATE(created_at)")));
    }

    private Map<String, Long> toDateMap(List<Map<String, Object>> rows) {
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("d");
            if (date != null) {
                result.put(date.toString(), asLong(row.get("c")));
            }
        }
        return result;
    }

    // ---------------- 分布 ----------------

    private PetDashboardVO.Distribution distribution() {
        return new PetDashboardVO.Distribution(
                bucket(petMapper.selectMaps(new QueryWrapper<Pet>()
                        .select("species AS name", "COUNT(*) AS c").groupBy("species"))),
                levelBuckets(),
                bucket(petMapper.selectMaps(new QueryWrapper<Pet>()
                        .select("career_code AS name", "COUNT(*) AS c")
                        .isNotNull("career_code")
                        .groupBy("career_code")
                        .orderByDesc("c")
                        .last("LIMIT 10"))),
                bucket(roomItemMapper.selectMaps(new QueryWrapper<PetRoomItem>()
                        .select("furniture_code AS name", "COUNT(*) AS c")
                        .groupBy("furniture_code")
                        .orderByDesc("c")
                        .last("LIMIT 10"))),
                bucket(petMapper.selectMaps(new QueryWrapper<Pet>()
                        .select("CONCAT(name, ' (', id, ')') AS name", "intimacy AS c")
                        .orderByDesc("intimacy")
                        .last("LIMIT 10"))));
    }

    private List<PetDashboardVO.Bucket> levelBuckets() {
        long baby = petMapper.selectCount(new QueryWrapper<Pet>().lt("level", 10));
        long young = petMapper.selectCount(new QueryWrapper<Pet>().ge("level", 10).lt("level", 20));
        long adult = petMapper.selectCount(new QueryWrapper<Pet>().ge("level", 20));
        return List.of(new PetDashboardVO.Bucket("幼年(1-9)", baby),
                new PetDashboardVO.Bucket("成长(10-19)", young),
                new PetDashboardVO.Bucket("成年(20+)", adult));
    }

    private List<PetDashboardVO.Bucket> bucket(List<Map<String, Object>> rows) {
        List<PetDashboardVO.Bucket> buckets = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("name");
            if (name != null) {
                buckets.add(new PetDashboardVO.Bucket(name.toString(), asLong(row.get("c"))));
            }
        }
        return buckets;
    }

    private static long scalar(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        return asLong(rows.get(0).get(key));
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
