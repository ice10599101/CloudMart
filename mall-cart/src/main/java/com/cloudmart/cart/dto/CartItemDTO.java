package com.cloudmart.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "购物车项DTO")
public record CartItemDTO(
    @Schema(description = "购物车项ID") Long id,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "数量") Integer quantity,
    @Schema(description = "是否选中") Integer checked,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "SKU图片") String skuImage,
    @Schema(description = "SKU属性") String skuAttributes,
    @Schema(description = "价格") BigDecimal price,
    @Schema(description = "T08 是否失效（下架/删除/变价）；业务仅产生两态：null=有效/未打标（展示按有效，结算侧独立核销），true=已失效") Boolean invalid,
    @Schema(description = "失效原因（invalid=true 时可读）") String invalidReason
) {
}
