package com.cloudmart.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 聊天响应")
public record ChatResponse(
    @Schema(description = "AI 回复内容") String reply,
    @Schema(description = "会话ID") String conversationId,
    @Schema(description = "是否降级到关键词搜索") boolean degraded
) {}
