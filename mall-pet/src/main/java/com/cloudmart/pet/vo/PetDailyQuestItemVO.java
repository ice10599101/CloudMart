package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 每日任务项（三期）。{@code claimable} 与 {@code statusLabel} 由服务端计算，
 * 前端只按布尔值渲染按钮（不做业务判断）。
 */
@Schema(description = "宠物每日任务项")
public record PetDailyQuestItemVO(
        @Schema(description = "任务编码") String code,
        @Schema(description = "任务名") String name,
        @Schema(description = "任务描述") String description,
        @Schema(description = "图标（emoji）") String icon,
        @Schema(description = "统计口径") String questType,
        @Schema(description = "当前进度") Integer progress,
        @Schema(description = "目标值") Integer targetValue,
        @Schema(description = "状态：IN_PROGRESS/COMPLETE/CLAIMED") String status,
        @Schema(description = "状态文案（进行中/可领取/已领取）") String statusLabel,
        @Schema(description = "是否可领奖") Boolean claimable,
        @Schema(description = "完成动作描述（B15：questType → 客户端跳转动作，服务端权威）") String actionTarget,
        @Schema(description = "奖励经验") Integer expReward,
        @Schema(description = "奖励星光") Integer currencyReward
) {
}
