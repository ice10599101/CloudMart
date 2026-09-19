package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 职业列表项（三期）：{@code current} 当前在职、{@code eligible} 可入职、
 * {@code lockReason} 不可入职原因（直接展示，不用前端拼文案）。
 */
@Schema(description = "宠物职业项")
public record PetCareerItemVO(
        @Schema(description = "职业编码") String code,
        @Schema(description = "职业名") String name,
        @Schema(description = "职业描述") String description,
        @Schema(description = "职业路线") String careerLine,
        @Schema(description = "阶段（1 初级/2 中级/3 高级）") Integer tier,
        @Schema(description = "图标") String icon,
        @Schema(description = "入职最低等级") Integer requiredLevel,
        @Schema(description = "入职最低智力") Integer requiredIntelligence,
        @Schema(description = "单次工作耗时（秒）") Integer durationSeconds,
        @Schema(description = "精力消耗") Integer energyCost,
        @Schema(description = "饥饿消耗") Integer hungerCost,
        @Schema(description = "基础经验奖励") Integer expReward,
        @Schema(description = "基础星光奖励") Integer currencyReward,
        @Schema(description = "我在此职业的工作次数") Integer workCount,
        @Schema(description = "是否当前在职") Boolean current,
        @Schema(description = "是否可入职") Boolean eligible,
        @Schema(description = "不可入职原因（null = 可入职）") String lockReason,
        @Schema(description = "晋升目标职业名（null = 已最高阶）") String promoteToName,
        @Schema(description = "晋升所需工作次数") Integer promoteRequiredCount,
        @Schema(description = "晋升消耗星光") Integer promoteStarCost,
        @Schema(description = "当前工作次数（未入职为 0）") Integer promoteCurrentCount,
        @Schema(description = "是否满足晋升条件") Boolean canPromote,
        @Schema(description = "晋升条件说明（不满足时展示）") String promoteLockReason
) {
}
