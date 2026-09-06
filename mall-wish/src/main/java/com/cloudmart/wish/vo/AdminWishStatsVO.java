package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理后台心愿宇宙统计 VO（管理工作台综合指标数据源）。
 *
 * @param totalWishCount       心愿总数（含软删外全部）
 * @param todayWishCount       今日新发布心愿数
 * @param activeWishCount       进行中心愿数（status=ACTIVE）
 * @param fulfilledWishCount   已实现心愿数（status=FULFILLED）
 * @param todayCheckinCount    今日打卡次数
 * @param todayInteractionCount 今日互动次数（点亮/同求/祝福）
 */
@Schema(description = "管理后台心愿宇宙统计")
public record AdminWishStatsVO(
        @Schema(description = "心愿总数") Long totalWishCount,
        @Schema(description = "今日新发布心愿数") Long todayWishCount,
        @Schema(description = "进行中心愿数") Long activeWishCount,
        @Schema(description = "已实现心愿数") Long fulfilledWishCount,
        @Schema(description = "今日打卡次数") Long todayCheckinCount,
        @Schema(description = "今日互动次数") Long todayInteractionCount
) {
}
