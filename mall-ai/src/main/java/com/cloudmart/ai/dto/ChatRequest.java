package com.cloudmart.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 聊天请求")
public record ChatRequest(
    @Schema(description = "用户消息") @NotBlank String message,
    @Schema(description = "会话ID，可选，用于多轮对话") String conversationId
) {}
