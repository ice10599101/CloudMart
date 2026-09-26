package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 装备替换预览（B12）：基础属性 / 当前总属性 / 替换后总属性 / 增量，服务端经 PetStatsService 复算。
 */
@Schema(description = "装备替换预览")
public record PetEquipPreviewVO(
        @Schema(description = "预览的装备编码") String itemCode,
        @Schema(description = "基础属性（无装备）: hp/maxHp/strength/intelligence/agility/charm") Stats base,
        @Schema(description = "当前总属性（现装备生效中）") Stats current,
        @Schema(description = "替换后总属性") Stats after,
        @Schema(description = "替换增量（after - current）") Stats delta
) {
    @Schema(description = "属性快照")
    public record Stats(int hp, int maxHp, int strength, int intelligence, int agility, int charm) {
    }
}
