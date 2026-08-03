package com.cloudmart.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "购物车项VO")
public record CartItemVO(
    @Schema(description = "购物车项ID") Long id,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "商品图片") String productImage,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "SKU属性") String skuAttributes,
    @Schema(description = "单价") BigDecimal price,
    @Schema(description = "数量") Integer quantity,
    @Schema(description = "小计") BigDecimal subtotal,
    @Schema(description = "是否选中") Boolean selected
) {}
