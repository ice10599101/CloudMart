package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "评价VO")
public record ReviewVO(
    @Schema(description = "评价ID") Long id,
    @Schema(description = "用户名") String username,
    @Schema(description = "评分") Integer rating,
    @Schema(description = "评价内容") String content,
    @Schema(description = "评价图片") List<String> images,
    @Schema(description = "状态：1=显示 0=下架") Integer status,
    @Schema(description = "R08 订单行锚点") Long orderItemId,
    @Schema(description = "订单 ID") Long orderId,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "商品 ID") Long productId,
    @Schema(description = "R08 下架/驳回理由（作者与后台可见）") String hideReason,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
