package com.cloudmart.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "评论摘要响应")
public record ReviewSummaryResponse(
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "优点摘要") String pros,
    @Schema(description = "缺点摘要") String cons,
    @Schema(description = "总体评价") String overall,
    @Schema(description = "评论总数") int totalReviews,
    @Schema(description = "是否降级") boolean degraded
) {}
