package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 统一活动视图（打工/读书/捞瓶共用；状态机在服务端，前端只展示）。
 */
@Schema(description = "宠物活动")
public record PetActivityVO(
        Long activityId,
        String activityType,
        Long configId,
        @Schema(description = "岗位/课程名（捞瓶/休息为 null）") String configName,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        @Schema(description = "剩余秒数（未完成为 0）") Long remainingSeconds,
        @Schema(description = "是否可领取奖励") Boolean canClaim,
        LocalDateTime claimedAt,
        @Schema(description = "结果 JSON（领取后回填）") String result
) {
}
