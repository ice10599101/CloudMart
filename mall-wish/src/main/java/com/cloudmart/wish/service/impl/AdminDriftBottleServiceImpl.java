package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminBottleListQuery;
import com.cloudmart.wish.entity.DriftBottle;
import com.cloudmart.wish.entity.DriftBottleComment;
import com.cloudmart.wish.entity.DriftBottleFishLog;
import com.cloudmart.wish.enums.DriftBottleStatus;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.DriftBottleCommentMapper;
import com.cloudmart.wish.repository.DriftBottleFishLogMapper;
import com.cloudmart.wish.repository.DriftBottleMapper;
import com.cloudmart.wish.service.AdminDriftBottleService;
import com.cloudmart.wish.vo.AdminDriftBottleCommentVO;
import com.cloudmart.wish.vo.AdminDriftBottleDashboardVO;
import com.cloudmart.wish.vo.AdminDriftBottleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 管理后台漂流瓶服务实现。
 *
 * <p>看板聚合全部走 GROUP BY 聚合查询（STATUS 分布 / 按日趋势 / 投瓶榜），
 * SQL 片段均为静态模板（无用户输入拼接），时间边界统一 UTC 自然日。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDriftBottleServiceImpl implements AdminDriftBottleService {

    private static final int TREND_DAYS = 14;
    private static final int TOP_THROWER_LIMIT = 10;
    private static final DateTimeFormatter TREND_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_NICKNAME = "心愿旅人";

    private final DriftBottleMapper bottleMapper;
    private final DriftBottleCommentMapper commentMapper;
    private final DriftBottleFishLogMapper fishLogMapper;
    private final UserFeignClient userFeignClient;

    @Override
    public Page<AdminDriftBottleVO> listBottles(AdminBottleListQuery query) {
        LambdaQueryWrapper<DriftBottle> wrapper = new LambdaQueryWrapper<DriftBottle>()
                .eq(query.status() != null, DriftBottle::getStatus, query.status())
                .orderByDesc(DriftBottle::getId);
        if (query.userId() != null) {
            // 投瓶人与捞瓶人任一匹配（管理端双向溯源）
            wrapper.and(w -> w.eq(DriftBottle::getThrowerUserId, query.userId())
                    .or()
                    .eq(DriftBottle::getPickerUserId, query.userId()));
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String keyword = query.keyword().trim();
            wrapper.and(w -> w.like(DriftBottle::getContent, keyword)
                    .or()
                    .like(DriftBottle::getWishTitle, keyword));
        }

        Page<DriftBottle> page = bottleMapper.selectPage(new Page<>(query.page(), query.pageSize()), wrapper);
        List<DriftBottle> records = page.getRecords();
        Map<Long, Long> commentCounts = countComments(records);
        Map<Long, String> nicknames = fetchNicknames(collectParticipantIds(records));

        Page<AdminDriftBottleVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records.stream()
                .map(b -> toAdminVo(b, commentCounts, nicknames))
                .toList());
        return result;
    }

    @Override
    public AdminDriftBottleDetail detail(Long bottleId) {
        DriftBottle bottle = bottleMapper.selectById(bottleId);
        if (bottle == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "漂流瓶不存在");
        }
        List<DriftBottleComment> comments = commentMapper.selectList(new LambdaQueryWrapper<DriftBottleComment>()
                .eq(DriftBottleComment::getBottleId, bottleId)
                .orderByDesc(DriftBottleComment::getId));
        Set<Long> userIds = new HashSet<>(collectParticipantIds(List.of(bottle)));
        comments.forEach(c -> userIds.add(c.getUserId()));
        Map<Long, String> nicknames = fetchNicknames(userIds);

        AdminDriftBottleVO vo = toAdminVo(bottle,
                countComments(List.of(bottle)), nicknames);
        List<AdminDriftBottleCommentVO> commentVos = comments.stream()
                .map(c -> new AdminDriftBottleCommentVO(
                        c.getId(),
                        c.getBottleId(),
                        c.getUserId(),
                        nicknames.getOrDefault(c.getUserId(), DEFAULT_NICKNAME),
                        c.getParentId(),
                        c.getContent(),
                        Boolean.TRUE.equals(c.getIsAnonymous()),
                        c.getCreatedAt()))
                .toList();
        return new AdminDriftBottleDetail(vo, commentVos);
    }

    @Override
    public AdminDriftBottleVO updateHidden(Long bottleId, boolean isHidden) {
        DriftBottle bottle = bottleMapper.selectById(bottleId);
        if (bottle == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "漂流瓶不存在");
        }
        bottleMapper.update(null, new LambdaUpdateWrapper<DriftBottle>()
                .set(DriftBottle::getIsHidden, isHidden)
                .eq(DriftBottle::getId, bottleId));
        bottle.setIsHidden(isHidden);
        return toAdminVo(bottle, countComments(List.of(bottle)), Map.of());
    }

    @Override
    public AdminDriftBottleDashboardVO dashboard() {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime trendStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();

        // 物理状态分布（不含已下架）
        Map<String, Long> statusCounts = new HashMap<>();
        for (Map<String, Object> row : bottleMapper.selectMaps(new QueryWrapper<DriftBottle>()
                .select("status", "COUNT(*) AS cnt")
                .eq("is_hidden", 0)
                .groupBy("status"))) {
            statusCounts.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        long floating = statusCounts.getOrDefault(DriftBottleStatus.FLOATING.name(), 0L);
        long picked = statusCounts.getOrDefault(DriftBottleStatus.PICKED.name(), 0L);
        long returned = statusCounts.getOrDefault(DriftBottleStatus.RETURNED.name(), 0L);
        long total = floating + picked + returned;

        long hiddenCount = orZero(bottleMapper.selectCount(new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getIsHidden, true)));
        long collectedCount = orZero(bottleMapper.selectCount(new LambdaQueryWrapper<DriftBottle>()
                .eq(DriftBottle::getIsCollected, true)
                .eq(DriftBottle::getIsHidden, false)));
        // 有评论的瓶子数（含被扔回海里但留下评论的瓶子）；静态 exists 片段，无外部输入
        long repliedCount = orZero(bottleMapper.selectCount(new QueryWrapper<DriftBottle>()
                .eq("is_hidden", 0)
                .exists("SELECT 1 FROM wish_drift_bottle_comment c WHERE c.bottle_id = wish_drift_bottle.id")));

        long todayThrowCount = orZero(bottleMapper.selectCount(new LambdaQueryWrapper<DriftBottle>()
                .ge(DriftBottle::getThrownAt, todayStart)));
        long todayFishCount = orZero(fishLogMapper.selectCount(new LambdaQueryWrapper<DriftBottleFishLog>()
                .ge(DriftBottleFishLog::getCreatedAt, todayStart)));
        long todayCommentCount = orZero(commentMapper.selectCount(new LambdaQueryWrapper<DriftBottleComment>()
                .ge(DriftBottleComment::getCreatedAt, todayStart)));

        List<AdminDriftBottleDashboardVO.DailyTrendItem> trend =
                buildTrend(trendStart, TREND_DAYS);
        List<AdminDriftBottleDashboardVO.ThrowerRankItem> topThrowers = buildTopThrowers();

        return new AdminDriftBottleDashboardVO(
                total, floating, picked, returned, repliedCount, collectedCount, hiddenCount,
                todayThrowCount, todayFishCount, todayCommentCount, trend, topThrowers);
    }

    /** 近 N 天投瓶/打捞双指标趋势（UTC 日期升序，缺数日补零） */
    private List<AdminDriftBottleDashboardVO.DailyTrendItem> buildTrend(LocalDateTime trendStart, int days) {
        Map<String, Long> throwByDay = groupCountByDay(bottleMapper.selectMaps(new QueryWrapper<DriftBottle>()
                .select("DATE_FORMAT(thrown_at, '%Y-%m-%d') AS day", "COUNT(*) AS cnt")
                .ge("thrown_at", trendStart)
                .groupBy("day")));
        Map<String, Long> fishByDay = groupCountByDay(fishLogMapper.selectMaps(new QueryWrapper<DriftBottleFishLog>()
                .select("DATE_FORMAT(created_at, '%Y-%m-%d') AS day", "COUNT(*) AS cnt")
                .ge("created_at", trendStart)
                .groupBy("day")));

        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        List<AdminDriftBottleDashboardVO.DailyTrendItem> trend = new ArrayList<>(days);
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(TREND_DATE_FORMAT);
            trend.add(new AdminDriftBottleDashboardVO.DailyTrendItem(
                    date,
                    throwByDay.getOrDefault(date, 0L),
                    fishByDay.getOrDefault(date, 0L)));
        }
        return trend;
    }

    /** 投瓶榜 Top10（不含已下架，Feign 失败降级占位昵称） */
    private List<AdminDriftBottleDashboardVO.ThrowerRankItem> buildTopThrowers() {
        List<Map<String, Object>> rows = bottleMapper.selectMaps(new QueryWrapper<DriftBottle>()
                .select("thrower_user_id AS uid", "COUNT(*) AS cnt")
                .eq("is_hidden", 0)
                .groupBy("thrower_user_id")
                .orderByDesc("cnt")
                .last("LIMIT " + TOP_THROWER_LIMIT));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(((Number) row.get("uid")).longValue(), ((Number) row.get("cnt")).longValue());
        }
        Map<Long, String> nicknames = fetchNicknames(counts.keySet());
        return counts.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(e -> new AdminDriftBottleDashboardVO.ThrowerRankItem(
                        e.getKey(),
                        nicknames.getOrDefault(e.getKey(), DEFAULT_NICKNAME),
                        e.getValue()))
                .toList();
    }

    private Map<String, Long> groupCountByDay(List<Map<String, Object>> rows) {
        Map<String, Long> result = new TreeMap<>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("day");
            Object cnt = row.get("cnt");
            if (day != null && cnt != null) {
                result.put(String.valueOf(day), ((Number) cnt).longValue());
            }
        }
        return result;
    }

    private long orZero(Long count) {
        return count == null ? 0 : count;
    }

    /** 每瓶评论数（bottle_id → cnt）；空列表直接返回空 Map */
    private Map<Long, Long> countComments(List<DriftBottle> bottles) {
        if (bottles.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = bottles.stream().map(DriftBottle::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Map<String, Object> row : commentMapper.selectMaps(new QueryWrapper<DriftBottleComment>()
                .select("bottle_id", "COUNT(*) AS cnt")
                .in("bottle_id", ids)
                .groupBy("bottle_id"))) {
            counts.put(((Number) row.get("bottle_id")).longValue(),
                    ((Number) row.get("cnt")).longValue());
        }
        return counts;
    }

    private Set<Long> collectParticipantIds(List<DriftBottle> bottles) {
        Set<Long> ids = new HashSet<>();
        for (DriftBottle bottle : bottles) {
            ids.add(bottle.getThrowerUserId());
            if (bottle.getPickerUserId() != null) {
                ids.add(bottle.getPickerUserId());
            }
        }
        return ids;
    }

    /** 批量获取昵称（Feign 失败降级空 Map，Fail Open：展示端按占位昵称渲染） */
    private Map<Long, String> fetchNicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream().collect(Collectors.toMap(
                        m -> ((Number) m.get("id")).longValue(),
                        m -> (String) m.getOrDefault("nickname", DEFAULT_NICKNAME)));
            }
        } catch (Exception e) {
            log.warn("管理端批量获取漂流瓶用户昵称失败，降级为占位昵称: {}", e.getMessage());
        }
        return Map.of();
    }

    private AdminDriftBottleVO toAdminVo(DriftBottle bottle,
                                         Map<Long, Long> commentCounts, Map<Long, String> nicknames) {
        return new AdminDriftBottleVO(
                bottle.getId(),
                bottle.getContent(),
                bottle.getWishId(),
                bottle.getWishTitle(),
                bottle.getStatus().name(),
                Boolean.TRUE.equals(bottle.getIsAnonymous()),
                bottle.getThrowerUserId(),
                nicknames.getOrDefault(bottle.getThrowerUserId(), DEFAULT_NICKNAME),
                Boolean.TRUE.equals(bottle.getPickerIsAnonymous()),
                bottle.getPickerUserId(),
                bottle.getPickerUserId() == null
                        ? null : nicknames.getOrDefault(bottle.getPickerUserId(), DEFAULT_NICKNAME),
                Boolean.TRUE.equals(bottle.getIsCollected()),
                bottle.getReturnCount(),
                Boolean.TRUE.equals(bottle.getIsHidden()),
                commentCounts.getOrDefault(bottle.getId(), 0L),
                bottle.getThrownAt(),
                bottle.getPickedAt());
    }
}
