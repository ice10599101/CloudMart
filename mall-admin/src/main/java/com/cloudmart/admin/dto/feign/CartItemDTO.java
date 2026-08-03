package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;

/**
 * 购物车项 Feign 传输对象，与 mall-cart 服务端 CartItemDTO 字段对齐
 */
public record CartItemDTO(
    Long id,
    Long userId,
    Long productId,
    Long skuId,
    Integer quantity,
    Integer checked,
    String productName,
    String skuImage,
    String skuAttributes,
    BigDecimal price
) {}
