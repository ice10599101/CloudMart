package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 宠物技能（原文档 §89 宠物技能）：全部配置 + 我的学习/背包状态。
 *
 * <p>{@code bookOwned}=背包有技能书但未学习；{@code learned}=已学会（带 equipped）。</p>
 */
@Schema(description = "宠物技能")
public record PetSkillVO(
        String code,
        String name,
        String description,
        @Schema(description = "类型: ACTIVE/PASSIVE") String skillType,
        @Schema(description = "效果标识（服务端公式开关）") String effect,
        @Schema(description = "效果数值（百分比为 0-1 小数，点数为整数）") BigDecimal effectValue,
        @Schema(description = "效果文案（服务端生成，前端直出）") String effectText,
        String icon,
        Integer priceStarlight,
        Integer requiredLevel,
        @Schema(description = "是否已学会") Boolean learned,
        @Schema(description = "是否已装配（已学会时有效）") Boolean equipped,
        @Schema(description = "背包是否已有技能书") Boolean bookOwned,
        @Schema(description = "是否满足学习条件") Boolean eligible,
        String lockReason
) {
}
