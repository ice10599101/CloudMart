package com.cloudmart.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "评论摘要VO")
public record ReviewSummaryVO(
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "摘要") String summary,
    @Schema(description = "好评比例") Double positiveRatio,
    @Schema(description = "差评比例") Double negativeRatio,
    @Schema(description = "评论总数") Integer totalReviews
) {}
