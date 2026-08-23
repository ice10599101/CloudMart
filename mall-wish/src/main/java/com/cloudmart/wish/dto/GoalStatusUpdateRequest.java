package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.GoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * AI 目标状态流转请求（文档 2.11：PUT /wish/ai/goals/{goalId}）。
 *
 * @param status  目标状态（PENDING→IN_PROGRESS→COMPLETED；非终态可 CANCELLED）
 */
@Schema(description = "AI 目标状态流转请求")
public record GoalStatusUpdateRequest(
        @Schema(description = "目标状态", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "目标状态不能为空")
        GoalStatus status
) {
}
