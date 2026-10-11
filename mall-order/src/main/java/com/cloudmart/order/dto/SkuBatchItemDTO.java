package com.cloudmart.order.dto;

import java.math.BigDecimal;

/**
 * 商品 SKU 批量信息（R24）：mall-product /products/skus/batch 的强类型响应契约。
 *
 * <p>此前 order 侧以 {@code List<Map<String,Object>>} 承接并以 {@code instanceof Number}
 * 判定 ID——雪花 ID 经 JsSafeLongSerializer 序列化为 JSON 字符串后会被当成"SKU 不存在"。
 * 强类型 DTO 由 Jackson 宽松反序列化（字符串数字 → Long），消除对 JSON 数字形态的假设。</p>
 */
public record SkuBatchItemDTO(
        Long skuId,
        Long productId,
        String productName,
        String image,
        BigDecimal price,
        Integer status,
        String attributes
) {
}
