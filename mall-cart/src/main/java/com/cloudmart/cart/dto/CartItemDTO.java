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
    @Schema(description = "T08 是否失效（下架/删除/变价），null 视为有效") Boolean invalid,
    @Schema(description = "失效原因（invalid=true 时可读）") String invalidReason
) {
    /** 兼容旧快照构造（Redis 缓存与既有调用点均为 10 参形状，失效标记缺省未打标） */
    public CartItemDTO(Long id, Long userId, Long productId, Long skuId, Integer quantity, Integer checked,
                       String productName, String skuImage, String skuAttributes, BigDecimal price) {
        this(id, userId, productId, skuId, quantity, checked, productName, skuImage, skuAttributes,
                price, null, null);
    }
}
