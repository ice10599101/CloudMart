package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * AI 意图分析+目标拆解响应 VO（文档 2.11：POST /wish/ai/assistant）。
 *
 * @param intent    意图概括
 * @param goals     拆解步骤（5-10 个，用户勾选后持久化）
 * @param suggestion 鼓励建议
 * @param sessionId AI 会话 ID（勾选持久化时回传关联对话）
 */
@Schema(description = "AI 意图分析+目标拆解结果")
public record AiBreakdownVO(
        @Schema(description = "意图概括") String intent,
        @Schema(description = "拆解步骤列表") List<AiBreakdownGoalVO> goals,
        @Schema(description = "鼓励建议") String suggestion,
        @Schema(description = "AI 会话 ID（勾选持久化时回传）") String sessionId
) {
}
