package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 宠物捞瓶状态视图。
 */
@Schema(description = "捞漂流瓶状态")
public record PetBottleStatusVO(
        @Schema(description = "是否正在捞瓶") Boolean fishing,
        Long activityId,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        @Schema(description = "剩余秒数") Long remainingSeconds,
        @Schema(description = "任务完成可领取") Boolean canClaim,
        @Schema(description = "冷却剩余秒数（上次空手/失败后）") Long cooldownRemainingSeconds,
        @Schema(description = "最近一次结果: CAUGHT/EMPTY/FAILED") String lastOutcome,
        Long lastBottleId,
        @Schema(description = "按当前宠物属性估算的成功率（仅展示）") Double estimatedSuccessRate,
        @Schema(description = "按等级解锁的捞瓶区域文案") String unlockedArea
) {
}
