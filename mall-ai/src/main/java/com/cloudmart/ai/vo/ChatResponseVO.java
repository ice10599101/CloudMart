package com.cloudmart.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

@Schema(description = "AI聊天响应VO")
public record ChatResponseVO(
    @Schema(description = "会话ID") String sessionId,
    @Schema(description = "AI回复") String reply,
    @Schema(description = "相关商品") List<Map<String, Object>> relatedProducts
) {}
