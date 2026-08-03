package com.cloudmart.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "AI搜索结果VO")
public record SearchResultVO(
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "商品名称") String name,
    @Schema(description = "价格") BigDecimal price,
    @Schema(description = "商品图片") String image,
    @Schema(description = "相关度评分") Double score
) {}
