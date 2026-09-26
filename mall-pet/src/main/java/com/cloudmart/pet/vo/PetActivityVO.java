package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 统一活动视图（打工/读书/捞瓶/职业工作/休息共用；状态机在服务端，前端只展示）。
 */
@Schema(description = "宠物活动")
public record PetActivityVO(
        Long activityId,
        @Schema(description = "实际执行宠物 ID（奖励归属依据，可能与当前主宠不同，B03）") Long petId,
        @Schema(description = "实际执行宠物名") String petName,
        String activityType,
        Long configId,
        @Schema(description = "活动名：岗位/课程名，捞瓶为「捞漂流瓶」，即时行为为 null") String configName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        @Schema(description = "剩余秒数（未完成为 0）") Long remainingSeconds,
        @Schema(description = "是否可领取奖励") Boolean canClaim,
        @Schema(description = "领取截止时间（COMPLETED 后 72 小时，过期置 EXPIRED）") LocalDateTime claimExpiresAt,
        LocalDateTime claimedAt,
        @Schema(description = "结果 JSON（领取后回填）") String result
) {
}
