package com.cloudmart.admin.dto.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * SKU 批量信息 Feign 传输对象（TRADE-02），与 mall-product SkuBatchItemVO 字段对齐：
 * 商品名 + SKU 属性（规格）用于库存列表展示补齐。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkuBatchDTO(
    Long skuId,
    Long productId,
    String productName,
    String image,
    BigDecimal price,
    Integer status,
    String attributes
) {}
