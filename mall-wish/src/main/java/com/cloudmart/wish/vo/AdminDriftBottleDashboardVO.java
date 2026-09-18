package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 漂流瓶管理数据看板。
 *
 * <p>statusCounts：物理状态分布；repliedCount 为「有评论的瓶子数」（含被扔回海里
 * 但留下评论的瓶子）；trend 为近 14 天（含今日，UTC）投瓶/打捞双指标趋势。</p>
 *
 * @param totalBottles       瓶子总数（不含已下架）
 * @param floatingCount      漂流中
 * @param pickedCount        已被捞起
 * @param returnedCount      被扔回海里
 * @param repliedCount       有评论的瓶子数
 * @param collectedCount     已被捞起人收藏
 * @param hiddenCount        已下架数
 * @param todayThrowCount    今日投瓶数
 * @param todayFishCount     今日打捞次数（含被扔回海里的打捞）
 * @param todayCommentCount  今日瓶下评论数
 * @param trend              近 14 天投瓶/打捞趋势（日期升序）
 * @param topThrowers        投瓶榜 Top10（不含已下架）
 */
@Schema(description = "漂流瓶管理数据看板")
public record AdminDriftBottleDashboardVO(
        long totalBottles,
        long floatingCount,
        long pickedCount,
        long returnedCount,
        long repliedCount,
        long collectedCount,
        long hiddenCount,
        long todayThrowCount,
        long todayFishCount,
        long todayCommentCount,
        List<DailyTrendItem> trend,
        List<ThrowerRankItem> topThrowers
) {

    /**
     * 每日趋势项。
     *
     * @param date      日期（UTC，yyyy-MM-dd）
     * @param throwCount 当日投瓶数
     * @param fishCount  当日打捞次数
     */
    @Schema(description = "每日投瓶/打捞趋势")
    public record DailyTrendItem(
            String date,
            long throwCount,
            long fishCount
    ) {
    }

    /**
     * 投瓶榜条目。
     *
     * @param userId     投瓶人用户 ID
     * @param nickname   昵称（Feign 失败降级占位）
     * @param throwCount 投瓶总数
     */
    @Schema(description = "投瓶榜条目")
    public record ThrowerRankItem(
            Long userId,
            String nickname,
            long throwCount
    ) {
    }
}
