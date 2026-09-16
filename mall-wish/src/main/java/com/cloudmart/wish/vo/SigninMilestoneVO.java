package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 连续签到里程碑（签到页「连续签到额外奖励」）。
 *
 * @param milestoneDays   里程碑连续签到天数（7/14/30）
 * @param starlightReward 领取可获得的星光
 * @param expReward       领取可获得的经验
 * @param claimed         是否已领取（每里程碑仅一次）
 * @param claimable       当前是否可领取（连续天数达标且未领取）
 */
@Schema(description = "连续签到里程碑")
public record SigninMilestoneVO(
        @Schema(description = "里程碑连续签到天数") int milestoneDays,
        @Schema(description = "领取可获得星光") int starlightReward,
        @Schema(description = "领取可获得经验") int expReward,
        @Schema(description = "是否已领取") boolean claimed,
        @Schema(description = "当前是否可领取") boolean claimable) {
}