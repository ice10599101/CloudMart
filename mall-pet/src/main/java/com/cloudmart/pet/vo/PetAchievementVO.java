package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 成就视图（含未达成项，achieved=false 供成就墙灰显）。
 */
@Schema(description = "宠物成就")
public record PetAchievementVO(
        Long achievementId,
        String code,
        String name,
        String description,
        String icon,
        @Schema(description = "达成条件阈值") Integer conditionValue,
        Integer expReward,
        Boolean achieved,
        LocalDateTime achievedAt
) {
}
