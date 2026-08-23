package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.GoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI 拆解目标 VO（文档 2.11：PUT /wish/ai/goals/{goalId} 与列表项）。
 *
 * @param id            目标 ID
 * @param wishId        关联心愿 ID（可空）
 * @param title         步骤标题
 * @param description   步骤描述
 * @param estimatedDays 预计完成天数
 * @param priority      优先级（1-5）
 * @param status        状态
 * @param aiSessionId   AI 会话 ID
 * @param startedAt     开始时间（UTC）
 * @param completedAt   完成时间（UTC）
 * @param createdAt     创建时间（UTC）
 */
@Schema(description = "AI 拆解目标")
public record AiGoalVO(
        @Schema(description = "目标 ID") Long id,
        @Schema(description = "关联心愿 ID") Long wishId,
        @Schema(description = "步骤标题") String title,
        @Schema(description = "步骤描述") String description,
        @Schema(description = "预计完成天数") Integer estimatedDays,
        @Schema(description = "优先级（1-5）") Integer priority,
        @Schema(description = "状态") GoalStatus status,
        @Schema(description = "AI 会话 ID") String aiSessionId,
        @Schema(description = "开始时间（UTC）") LocalDateTime startedAt,
        @Schema(description = "完成时间（UTC）") LocalDateTime completedAt,
        @Schema(description = "创建时间（UTC）") LocalDateTime createdAt
) {
}
