package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * AI 拆解步骤项 VO（文档 2.11：POST /wish/ai/assistant 响应 goals 元素）。
 *
 * @param title         步骤标题
 * @param description   步骤描述
 * @param estimatedDays 预计完成天数
 * @param priority      优先级（1-5，1 最高）
 */
@Schema(description = "AI 拆解步骤")
public record AiBreakdownGoalVO(
        @Schema(description = "步骤标题") String title,
        @Schema(description = "步骤描述") String description,
        @Schema(description = "预计完成天数") Integer estimatedDays,
        @Schema(description = "优先级（1-5，1 最高）") Integer priority
) {
}
