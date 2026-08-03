package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Map;

@Schema(description = "评价统计VO")
public record ReviewStatsVO(
    @Schema(description = "平均评分") BigDecimal averageRating,
    @Schema(description = "评价总数") Integer totalCount,
    @Schema(description = "各星级分布") Map<Integer, Integer> distribution
) {}
