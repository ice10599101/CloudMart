package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 宠物背包物品（原文档 §6.1 宠物背包）。
 *
 * <p>装备与皮肤带 {@code equipped} 标记；皮肤额外回传 color/accessory 供前端预览，
 * 装备回传属性加成供前端展示"装备生效中"的数值来源。</p>
 */
@Schema(description = "宠物背包物品")
public record PetInventoryItemVO(
        @Schema(description = "物品类型: EQUIPMENT/SKIN/SKILL_BOOK") String itemType,
        String code,
        String name,
        String description,
        String icon,
        String rarity,
        @Schema(description = "装备部位（装备类）") String slot,
        @Schema(description = "主色（皮肤类）") String color,
        @Schema(description = "配饰（皮肤类）") String accessory,
        @Schema(description = "技能效果标识（技能书类）") String effect,
        @Schema(description = "效果数值（技能书类）") BigDecimal effectValue,
        Integer bonusStrength,
        Integer bonusIntelligence,
        Integer bonusAgility,
        Integer bonusCharm,
        Integer bonusMaxHp,
        @Schema(description = "是否装备/穿戴中") Boolean equipped,
        Integer quantity,
        @Schema(description = "是否已使用（技能书学习后置 true，前端灰显）") Boolean used,
        LocalDateTime acquiredAt
) {
}
