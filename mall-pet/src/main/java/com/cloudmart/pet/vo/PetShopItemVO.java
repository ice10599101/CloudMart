package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 宠物商城商品（装备/皮肤/技能书统一下发；原文档 §89 宠物商城）。
 *
 * <p>字段按 itemType 取舍：EQUIPMENT 用 slot/bonus*，SKIN 用 species/color/accessory，
 * SKILL_BOOK 用 skillType/effect/effectValue。前端据 owned/eligible 决定按钮态，
 * 服务端购买时二次校验（不信任客户端）。</p>
 */
@Schema(description = "宠物商城商品")
public record PetShopItemVO(
        @Schema(description = "物品类型: EQUIPMENT/SKIN/SKILL_BOOK") String itemType,
        String code,
        String name,
        String description,
        String icon,
        @Schema(description = "稀有度: COMMON/RARE/EPIC") String rarity,
        @Schema(description = "售价（星光）") Integer priceStarlight,
        @Schema(description = "装备部位（装备类）") String slot,
        @Schema(description = "限定种类（皮肤类，null=通用）") String species,
        @Schema(description = "主色（皮肤类）") String color,
        @Schema(description = "配饰（皮肤类）") String accessory,
        @Schema(description = "技能类型（技能书类）: ACTIVE/PASSIVE") String skillType,
        @Schema(description = "技能效果标识（技能书类）") String effect,
        @Schema(description = "效果数值（技能书类）") BigDecimal effectValue,
        Integer bonusStrength,
        Integer bonusIntelligence,
        Integer bonusAgility,
        Integer bonusCharm,
        Integer bonusMaxHp,
        Integer requiredLevel,
        Integer requiredEvolutionStage,
        @Schema(description = "是否已拥有") Boolean owned,
        @Schema(description = "当前是否满足购买条件") Boolean eligible,
        @Schema(description = "不可购买原因（eligible=false 时给出，可直接展示）") String lockReason
) {
}
