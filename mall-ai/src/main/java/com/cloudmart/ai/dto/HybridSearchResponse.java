package com.cloudmart.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "混合搜索响应")
public record HybridSearchResponse(
    @Schema(description = "向量检索结果") List<VectorSearchResult> vectorResults,
    @Schema(description = "全文检索结果") List<ProductSearchResult> keywordResults,
    @Schema(description = "AI 推荐说明") String explanation,
    @Schema(description = "是否降级") boolean degraded
) {}
