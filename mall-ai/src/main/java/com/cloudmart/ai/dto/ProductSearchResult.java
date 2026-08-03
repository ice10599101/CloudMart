package com.cloudmart.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "AI 搜索商品结果")
public record ProductSearchResult(
    @Schema(description = "商品ID") Long id,
    @Schema(description = "商品名称") String name,
    @Schema(description = "描述") String description,
    @Schema(description = "价格") BigDecimal price,
    @Schema(description = "主图URL") String mainImage,
    @Schema(description = "分类名称") String categoryName,
    @Schema(description = "相关度评分") Double score
) {}
