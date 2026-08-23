package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 助手意图分析+目标拆解请求（文档 2.11：POST /wish/ai/assistant）。
 *
 * @param text    心愿/目标描述（≤1000 字）
 * @param wishId  关联心愿 ID（可选）
 */
@Schema(description = "AI 助手意图分析+目标拆解请求")
public record AiAssistantRequest(
        @Schema(description = "心愿/目标描述", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "目标描述不能为空")
        @Size(max = 1000, message = "目标描述最长 1000 字")
        String text,

        @Schema(description = "关联心愿 ID（可选，用于拆解上下文关联）")
        Long wishId
) {
}
