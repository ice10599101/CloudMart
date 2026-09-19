package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 读书课程出参。
 */
@Schema(description = "读书课程")
public record PetStudyVO(
        Long configId,
        String name,
        String description,
        String category,
        Integer durationSeconds,
        Integer energyCost,
        Integer expReward,
        Integer intelligenceReward,
        Integer requiredLevel,
        Boolean eligible
) {
}
