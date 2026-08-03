package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;

/**
 * 订单项 Feign 传输对象，与 mall-order 服务端 OrderItemDTO 字段对齐
 */
public record OrderItemDTO(
    Long id,
    Long productId,
    Long skuId,
    String productName,
    String skuImage,
    String skuAttributes,
    BigDecimal price,
    Integer quantity
) {}
