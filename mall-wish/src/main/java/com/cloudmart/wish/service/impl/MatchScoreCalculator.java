package com.cloudmart.wish.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 匹配评分纯函数（Sprint 2.6，文档十章：关键词 0.4/城市 0.3/活跃度 0.3，
 * 权重可配置）。无状态静态方法，可独立单测。
 *
 * <p>活跃度衰减：半衰期 7 天的指数衰减 {@code exp(-ln2 × 天数 / 7)}——
 * 昨日活跃≈0.9，7 天未活跃≈0.5，28 天≈0.06；冷启动用户（无记录）得 0，
 * 依赖关键词维度优先匹配（文档验收：新用户无打卡记录也能匹配）。</p>
 */
public final class MatchScoreCalculator {

    /** 活跃度半衰期（天）：7 天未活跃衰减到 0.5 */
    private static final double ACTIVITY_HALF_LIFE_DAYS = 7.0;

    private MatchScoreCalculator() {
    }

    /**
     * 评分明细（含相似度说明，三端文案由 API 下发保持一致）。
     *
     * @param keywordScore  关键词命中分（0 或 1，命中=相等或互为包含）
     * @param cityScore     同城分（0 或 1，city_code 相等）
     * @param activityScore 小组成员平均活跃度（0-1）
     * @param total         加权总分（0-1，权重和为 0 时得 0）
     * @param reasons       相似度说明列表（如"你们都想看极光"）
     */
    public record ScoreBreakdown(double keywordScore, double cityScore, double activityScore,
                                 double total, List<String> reasons) {
    }

    /**
     * 单用户活跃度分：按 lastActiveAt 距今天数指数衰减；null（从未活跃）得 0。
     */
    public static double activityScore(LocalDateTime lastActiveAt, LocalDateTime now) {
        if (lastActiveAt == null || lastActiveAt.isAfter(now)) {
            return lastActiveAt != null ? 1.0 : 0.0;
        }
        double days = Duration.between(lastActiveAt, now).toHours() / 24.0;
        if (days < 0) {
            days = 0;
        }
        return Math.exp(-Math.log(2) * days / ACTIVITY_HALF_LIFE_DAYS);
    }

    /**
     * 小组活跃度分：ACTIVE 成员活跃度的平均值（无成员得 0）。
     */
    public static double groupActivityScore(List<LocalDateTime> memberLastActiveTimes, LocalDateTime now) {
        if (memberLastActiveTimes == null || memberLastActiveTimes.isEmpty()) {
            return 0.0;
        }
        return memberLastActiveTimes.stream()
                .mapToDouble(t -> activityScore(t, now))
                .average()
                .orElse(0.0);
    }

    /**
     * 关键词分：查询关键词/用户标签与组主题命中（相等或互为包含）得 1，否则 0。
     * 文档验收"冷启动基于关键词优先"由此保证——关键词命中时权重占主导。
     */
    public static double keywordScore(String queryKeyword, List<String> userTags, String groupKeyword) {
        if (groupKeyword == null || groupKeyword.isBlank()) {
            return 0.0;
        }
        if (queryKeyword != null && !queryKeyword.isBlank()) {
            return containsEither(queryKeyword, groupKeyword) ? 1.0 : 0.0;
        }
        if (userTags != null) {
            for (String tag : userTags) {
                if (tag != null && containsEither(tag, groupKeyword)) {
                    return 1.0;
                }
            }
        }
        return 0.0;
    }

    /**
     * 加权总分（0-1）。权重和为 0（配置错误）时退化为等权平均，
     * 避免除零且保证配置容错（Fail-Open 原则）。
     */
    public static ScoreBreakdown score(double keywordScore, double cityScore, double activityScore,
                                       double weightKeyword, double weightCity, double weightActivity,
                                       String groupKeyword) {
        double weightSum = weightKeyword + weightCity + weightActivity;
        double total;
        if (weightSum <= 0) {
            total = (keywordScore + cityScore + activityScore) / 3.0;
        } else {
            total = (keywordScore * weightKeyword + cityScore * weightCity + activityScore * weightActivity)
                    / weightSum;
        }
        total = Math.max(0.0, Math.min(1.0, total));

        List<String> reasons = new ArrayList<>();
        if (keywordScore >= 1.0) {
            reasons.add("你们都想" + groupKeyword);
        }
        if (cityScore >= 1.0) {
            reasons.add("你们可能是同城伙伴");
        }
        if (activityScore >= 0.5) {
            reasons.add("组员们最近也在坚持打卡");
        }
        if (reasons.isEmpty()) {
            reasons.add("同愿热度推荐");
        }
        return new ScoreBreakdown(keywordScore, cityScore, activityScore, total, List.copyOf(reasons));
    }

    private static boolean containsEither(String a, String b) {
        return a.contains(b) || b.contains(a);
    }
}
