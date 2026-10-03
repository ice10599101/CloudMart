package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 一键领奖逐项结果（R13/§7.2）：每项独立事务的明确终态——
 * 禁止"整体 success 掩盖单项失败"，失败项带 errorCode 供前端定位重试。
 */
@Schema(description = "任务领取单项结果")
public record QuestClaimResult(
        @Schema(description = "任务编码（宝箱为 chest）") String code,
        @Schema(description = "任务 ID") String questId,
        @Schema(description = "CLAIMED/ALREADY_CLAIMED/NOT_READY/FAILED") String status,
        @Schema(description = "奖励经验（仅 CLAIMED）") Integer expReward,
        @Schema(description = "奖励星光（仅 CLAIMED）") Integer currencyReward,
        @Schema(description = "失败/未领原因码") String errorCode
) {
}
