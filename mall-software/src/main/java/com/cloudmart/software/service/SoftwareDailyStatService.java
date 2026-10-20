package com.cloudmart.software.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.software.entity.SoftwareDailyStat;
import com.cloudmart.software.entity.SoftwareStatEvent;
import com.cloudmart.software.repository.SoftwareDailyStatMapper;
import com.cloudmart.software.repository.SoftwareStatEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * SF06 可解释统计：日聚合 rebuild 与趋势查询。
 *
 * <p>口径（metricDefinition 同步返回给前端展示）：
 * <ul>
 *   <li>获取链接次数/人数：software_link_access_daily（站内去重获取，非外部下载量）；</li>
 *   <li>收藏/订阅净增：新增与取消分别记录（事件表），净值=新增−取消；</li>
 *   <li>审核处理时长：PENDING 到最终人工决定（CHECKING 时间另列）；</li>
 *   <li>反馈修复时长：首次有效反馈到核验修复上线，拒绝项另列；</li>
 *   <li>投稿漏斗：提交受理→人工通过→成功发布（各按唯一事实计数）。</li>
 * </ul>
 * 重复 rebuild 不累计翻倍（唯一键 + 先删后插同事务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftwareDailyStatService {

    /** 平台时区：与 software_link_access_daily 切日一致 */
    private static final ZoneId PLATFORM_ZONE = ZoneId.of("Asia/Shanghai");

    /** 趋势窗口上限（方案 SF06：最长 90 天） */
    private static final int MAX_RANGE_DAYS = 90;

    private final SoftwareStatEventMapper eventMapper;
    private final SoftwareDailyStatMapper dailyStatMapper;
    private final com.cloudmart.software.repository.SoftwareRevisionMapper revisionMapper;
    private final com.cloudmart.software.repository.SoftwareLinkFeedbackMapper feedbackMapper;
    private final com.cloudmart.software.repository.SoftwareReviewCheckTaskMapper checkTaskMapper;
    private final Clock clock;

    /** 记录收藏/订阅事件（Asia/Shanghai 切日；写入失败不阻断主流程——统计可重建） */
    public void recordEvent(Long softwareId, Long userId, String kind) {
        try {
            SoftwareStatEvent event = new SoftwareStatEvent();
            event.setSoftwareId(softwareId);
            event.setUserId(userId);
            event.setKind(kind);
            LocalDate today = LocalDate.now(clock.withZone(PLATFORM_ZONE));
            event.setEventDate(today);
            event.setCreatedAt(LocalDateTime.now(clock));
            eventMapper.insert(event);
        } catch (Exception e) {
            // 事实事件缺失可由 rebuild 从关系表之外的事件日志补齐失败时接受短期偏差；
            // 记录告警供巡检（统计正确性优先级低于用户主流程可用性）
            log.warn("统计事件写入失败 softwareId={} kind={}: {}", softwareId, kind, e.getMessage());
        }
    }

    /** 全量重算日期范围（同事务：先删后插；重复执行结果一致） */
    @Transactional
    public int rebuild(LocalDate from, LocalDate to) {
        int deleted = dailyStatMapper.deleteRange(from, to);
        int resolveRows = dailyStatMapper.insertResolveRange(from, to);
        int eventRows = dailyStatMapper.insertFavoriteSubscribeRange(from, to);
        log.info("SF06 日聚合重算 from={} to={} 删除 {} 行，resolve {} 组，事件 {} 组",
                from, to, deleted, resolveRows, eventRows);
        return resolveRows + eventRows;
    }

    /**
     * 趋势查询（admin/owner 共用；owner 传入本人软件 ID 集合）。
     * 返回补零日序列 + 明确 metricDefinition。
     */
    public Map<String, Object> trends(LocalDate from, LocalDate to,
                                      List<Long> softwareIds, String metric) {
        LocalDate today = LocalDate.now(clock.withZone(PLATFORM_ZONE));
        if (from == null) {
            from = today.minusDays(29);
        }
        if (to == null) {
            to = today;
        }
        if (from.isAfter(to)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "VALIDATION_ERROR", "from 不能晚于 to");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "VALIDATION_ERROR", "查询窗口最长 90 天");
        }

        // 按软件过滤查询聚合行，按日汇总（跨软件求和）
        List<SoftwareDailyStat> rows = dailyStatMapper.selectList(
                new LambdaQueryWrapper<SoftwareDailyStat>()
                        .between(SoftwareDailyStat::getStatDate, from, to)
                        .in(softwareIds != null && !softwareIds.isEmpty(),
                                SoftwareDailyStat::getSoftwareId, softwareIds));
        Map<LocalDate, long[]> byDate = new TreeMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            byDate.put(date, new long[6]);
        }
        for (SoftwareDailyStat row : rows) {
            long[] bucket = byDate.get(row.getStatDate());
            if (bucket == null) {
                continue;
            }
            bucket[0] += row.getResolveCount();
            bucket[1] += row.getResolveUserCount();
            bucket[2] += row.getFavoriteAdd();
            bucket[3] += row.getFavoriteRemove();
            bucket[4] += row.getSubscribeAdd();
            bucket[5] += row.getSubscribeRemove();
        }
        List<Map<String, Object>> series = new ArrayList<>();
        for (Map.Entry<LocalDate, long[]> entry : byDate.entrySet()) {
            long[] v = entry.getValue();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", entry.getKey().toString());
            point.put("resolveCount", v[0]);
            point.put("resolveUserCount", v[1]);
            point.put("favoriteAdd", v[2]);
            point.put("favoriteRemove", v[3]);
            point.put("subscribeAdd", v[4]);
            point.put("subscribeRemove", v[5]);
            point.put("favoriteNet", v[2] - v[3]);
            point.put("subscribeNet", v[4] - v[5]);
            series.add(point);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("granularity", "day");
        result.put("timezone", "Asia/Shanghai");
        result.put("series", series);
        result.put("metricDefinition", metricDefinitions());
        return result;
    }

    /** 指标口径字典（随响应返回，前端可见"禁止误用"说明） */
    private Map<String, String> metricDefinitions() {
        Map<String, String> defs = new LinkedHashMap<>();
        defs.put("resolveCount", "获取链接次数：站内去重获取事实（software_link_access_daily），"
                + "不是外部下载量或安装量");
        defs.put("resolveUserCount", "获取链接人数：当日去重用户数（同口径事实源）");
        defs.put("favoriteAdd", "新增收藏数（事件事实）");
        defs.put("favoriteRemove", "取消收藏数（事件事实）");
        defs.put("favoriteNet", "收藏净增 = 新增 − 取消");
        defs.put("subscribeAdd", "新增订阅数（事件事实）");
        defs.put("subscribeRemove", "取消订阅数（事件事实）");
        defs.put("subscribeNet", "订阅净增 = 新增 − 取消");
        return defs;
    }

    /**
     * 审核/修复效率（SF06）：直接从事实表计算，不进入日聚合表（低频）。
     * 审核时长=PENDING 到最终人工决定（分钟），CHECKING 时长由检查任务 finished_at
     * 单列（提交→检查完成）；无检查任务的存量提交按提交即进入 PENDING 处理。
     * 反馈修复时长=首次反馈到修复上线（分钟），未结项不计入分位（不填 0 混入均值），
     * 拒绝项另列。
     */
    public Map<String, Object> efficiency() {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Object> view = new LinkedHashMap<>();

        // 审核处理时长（近 30 天已审定提交）
        List<com.cloudmart.software.entity.SoftwareRevision> reviewed =
                revisionMapper.selectList(new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareRevision>()
                        .eq(com.cloudmart.software.entity.SoftwareRevision::getReviewStatus,
                                com.cloudmart.software.enums.SoftwareReviewStatus.APPROVED)
                        .isNotNull(com.cloudmart.software.entity.SoftwareRevision::getReviewedAt)
                        .ge(com.cloudmart.software.entity.SoftwareRevision::getReviewedAt,
                                now.minusDays(30)));
        // CHECKING 结束事实：检查任务 finished_at（同一提交唯一任务）
        Map<Long, LocalDateTime> checkFinishedAt = new HashMap<>();
        List<Long> revisionIds = reviewed.stream()
                .map(com.cloudmart.software.entity.SoftwareRevision::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!revisionIds.isEmpty()) {
            for (com.cloudmart.software.entity.SoftwareReviewCheckTask task : checkTaskMapper.selectList(
                    new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareReviewCheckTask>()
                            .in(com.cloudmart.software.entity.SoftwareReviewCheckTask::getRevisionId,
                                    revisionIds)
                            .isNotNull(com.cloudmart.software.entity.SoftwareReviewCheckTask::getFinishedAt))) {
                if (task.getRevisionId() != null && task.getFinishedAt() != null) {
                    checkFinishedAt.merge(task.getRevisionId(), task.getFinishedAt(),
                            (left, right) -> left.isAfter(right) ? left : right);
                }
            }
        }
        List<Long> checkMinutes = new ArrayList<>();
        List<Long> reviewMinutes = new ArrayList<>();
        for (var revision : reviewed) {
            LocalDateTime submitted = revision.getSubmittedAt();
            LocalDateTime reviewedAt = revision.getReviewedAt();
            if (submitted == null || reviewedAt == null || reviewedAt.isBefore(submitted)) {
                continue;
            }
            LocalDateTime checkEnd = checkFinishedAt.get(revision.getId());
            if (checkEnd != null && !checkEnd.isBefore(submitted)) {
                // CHECKING 单列；PENDING 从检查完成起算（自动发布为 0 分钟）
                checkMinutes.add(java.time.Duration.between(submitted, checkEnd).toMinutes());
                if (!reviewedAt.isBefore(checkEnd)) {
                    reviewMinutes.add(java.time.Duration.between(checkEnd, reviewedAt).toMinutes());
                }
            } else {
                reviewMinutes.add(java.time.Duration.between(submitted, reviewedAt).toMinutes());
            }
        }
        view.put("reviewCount", reviewMinutes.size());
        view.put("reviewP50Minutes", percentile(reviewMinutes, 50));
        view.put("reviewP95Minutes", percentile(reviewMinutes, 95));
        view.put("reviewAvgMinutes", reviewMinutes.isEmpty() ? null :
                reviewMinutes.stream().mapToLong(Long::longValue).average().orElse(0));
        view.put("checkCount", checkMinutes.size());
        view.put("checkP50Minutes", percentile(checkMinutes, 50));
        view.put("checkP95Minutes", percentile(checkMinutes, 95));
        view.put("checkAvgMinutes", checkMinutes.isEmpty() ? null :
                checkMinutes.stream().mapToLong(Long::longValue).average().orElse(0));
        view.put("metricDefinition", "审核时长=PENDING（检查完成后）到最终人工决定；"
                + "CHECKING 时长=提交到自动检查完成，单列统计；无检查任务的存量提交按提交即进入 PENDING");

        // 反馈修复时长（近 30 天 RESOLVED 反馈；拒绝项另列）
        List<com.cloudmart.software.entity.SoftwareLinkFeedback> feedbacks =
                feedbackMapper.selectList(new LambdaQueryWrapper<com.cloudmart.software.entity.SoftwareLinkFeedback>()
                        .ge(com.cloudmart.software.entity.SoftwareLinkFeedback::getCreatedAt,
                                now.minusDays(30)));
        List<Long> fixMinutes = new ArrayList<>();
        int rejected = 0;
        int open = 0;
        for (var feedback : feedbacks) {
            if (feedback.getStatus() == com.cloudmart.software.enums.LinkFeedbackStatus.RESOLVED
                    && feedback.getHandledAt() != null) {
                fixMinutes.add(java.time.Duration.between(
                        feedback.getCreatedAt(), feedback.getHandledAt()).toMinutes());
            } else if (feedback.getStatus() == com.cloudmart.software.enums.LinkFeedbackStatus.REJECTED) {
                rejected++;
            } else if (feedback.getStatus() != com.cloudmart.software.enums.LinkFeedbackStatus.RESOLVED) {
                open++;
            }
        }
        view.put("feedbackResolvedCount", fixMinutes.size());
        view.put("feedbackFixP50Minutes", percentile(fixMinutes, 50));
        view.put("feedbackFixP95Minutes", percentile(fixMinutes, 95));
        view.put("feedbackRejectedCount", rejected);
        view.put("feedbackOpenCount", open);
        return view;
    }

    /** 分位（线性插值；空列表返回 null） */
    private Double percentile(List<Long> values, int p) {
        if (values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        java.util.Collections.sort(sorted);
        double rank = (sorted.size() - 1) * p / 100.0;
        int low = (int) Math.floor(rank);
        int high = Math.min(low + 1, sorted.size() - 1);
        double fraction = rank - low;
        return Math.round((sorted.get(low) + (sorted.get(high) - sorted.get(low)) * fraction) * 10) / 10.0;
    }
}