package com.cloudmart.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "AI 搜索响应")
public record AiSearchResponse(
    @Schema(description = "搜索结果列表") List<ProductSearchResult> products,
    @Schema(description = "AI 推荐说明") String explanation,
    @Schema(description = "是否降级") boolean degraded
) {}
