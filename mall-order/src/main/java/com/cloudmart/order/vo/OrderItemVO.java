package com.cloudmart.order.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "订单项VO")
public record OrderItemVO(
    @Schema(description = "订单项ID") Long id,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "商品图片") String productImage,
    @Schema(description = "SKU属性") String skuAttributes,
    @Schema(description = "单价") BigDecimal price,
    @Schema(description = "数量") Integer quantity,
    @Schema(description = "小计") BigDecimal subtotal
) {}
