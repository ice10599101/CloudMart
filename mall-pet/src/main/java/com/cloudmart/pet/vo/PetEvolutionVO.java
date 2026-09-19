package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 宠物进化状态（原文档 §89 宠物进化）。
 *
 * <p>{@code nextStage} 为空表示已到最高阶；{@code canEvolve=false} 时
 * {@code lockReason} 给出可展示原因（等级不足/星光不足/无下一阶）。</p>
 */
@Schema(description = "宠物进化状态")
public record PetEvolutionVO(
        @Schema(description = "当前进化阶段") Integer currentStage,
        @Schema(description = "最高进化阶段") Integer maxStage,
        @Schema(description = "下一阶编码（最高阶时为 null）") String nextCode,
        @Schema(description = "下一阶名称") String nextName,
        @Schema(description = "下一阶描述") String nextDescription,
        @Schema(description = "下一阶所需等级") Integer requiredLevel,
        @Schema(description = "下一阶星光消耗") Integer costStarlight,
        @Schema(description = "下一阶生命上限提升") Integer bonusMaxHp,
        @Schema(description = "下一阶力量提升") Integer bonusStrength,
        @Schema(description = "下一阶智力提升") Integer bonusIntelligence,
        @Schema(description = "下一阶敏捷提升") Integer bonusAgility,
        @Schema(description = "下一阶魅力提升") Integer bonusCharm,
        @Schema(description = "下一阶解锁皮肤编码（可空）") String unlockSkinCode,
        @Schema(description = "下一阶图标") String icon,
        @Schema(description = "当前是否可进化") Boolean canEvolve,
        @Schema(description = "不可进化原因（可直接展示）") String lockReason
) {
}
