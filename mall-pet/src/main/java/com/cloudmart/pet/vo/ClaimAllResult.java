package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 一键领奖结果（R13/§7.2）：普通任务逐项结果 + 宝箱独立评估——
 * 不能只返回一个 success 提示（原实现仅宝箱可领时出现"空列表却提示都收好了"）。
 */
@Schema(description = "一键领奖逐项结果")
public record ClaimAllResult(
        @Schema(description = "普通任务逐项结果") List<QuestClaimResult> results,
        @Schema(description = "宝箱结果（普通项结束后重新评估，独立动作）") QuestClaimResult chest
) {
}
