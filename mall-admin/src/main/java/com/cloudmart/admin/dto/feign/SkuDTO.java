package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;

/**
 * SKU Feign 传输对象，与 mall-product 服务端 SkuDTO 字段对齐
 */
public record SkuDTO(
    Long id,
    Long productId,
    String skuCode,
    String attributes,
    BigDecimal price,
    BigDecimal originalPrice,
    Integer stock,
    String image,
    Integer status
) {}
