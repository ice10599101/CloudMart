package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 宠物摘要（多宠物切换列表；原文档 §89 多种宠物）。
 *
 * <p>只含列表展示所需字段，避免拉取多只宠物的完整状态导致响应膨胀；
 * 详细状态以主宠 {@link PetVO} 为准。</p>
 */
@Schema(description = "宠物摘要（多宠物列表）")
public record PetSummaryVO(
        Long petId,
        String name,
        String species,
        @Schema(description = "性别: MALE/FEMALE") String gender,
        String appearance,
        String personality,
        @Schema(description = "等级") Integer level,
        @Schema(description = "成长阶段") String growthStage,
        @Schema(description = "进化阶段") Integer evolutionStage,
        @Schema(description = "当前皮肤编码") String skinCode,
        @Schema(description = "生命值") Integer hp,
        Integer maxHp,
        Integer hunger,
        Integer happiness,
        Integer energy,
        Integer cleanliness,
        @Schema(description = "是否主宠") Boolean isActive
) {
}
