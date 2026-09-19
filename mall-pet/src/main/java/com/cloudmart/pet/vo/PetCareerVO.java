package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 宠物职业面板（三期）：当前职业摘要 + 全部职业（按路线/阶段排序）+ 工作历史。
 */
@Schema(description = "宠物职业面板")
public record PetCareerVO(
        @Schema(description = "当前职业编码（null = 未入职）") String careerCode,
        @Schema(description = "当前职业名") String careerName,
        @Schema(description = "当前职业路线") String careerLine,
        @Schema(description = "当前职业阶段") Integer tier,
        @Schema(description = "当前职业图标") String icon,
        @Schema(description = "当前职业累计工作次数") Integer workCount,
        @Schema(description = "进行中/待领取的职业工作") PetActivityVO activeActivity,
        @Schema(description = "是否可晋升") Boolean canPromote,
        @Schema(description = "晋升目标职业名") String promoteToName,
        @Schema(description = "晋升所需工作次数") Integer promoteRequiredCount,
        @Schema(description = "晋升消耗星光") Integer promoteStarCost,
        @Schema(description = "晋升条件说明（不满足时展示）") String promoteLockReason,
        @Schema(description = "职业列表") List<PetCareerItemVO> careers,
        @Schema(description = "工作历史（已离开的职业）") List<HistoryItem> history
) {
    /** 职业历史（已离开的职业保留次数统计） */
    @Schema(description = "宠物职业历史项")
    public record HistoryItem(
            @Schema(description = "职业编码") String code,
            @Schema(description = "职业名") String name,
            @Schema(description = "累计工作次数") Integer workCount,
            @Schema(description = "累计星光收入") Integer totalCurrency,
            @Schema(description = "入职时间") LocalDateTime startedAt,
            @Schema(description = "离开时间（晋升）") LocalDateTime promotedAt
    ) {
    }
}
