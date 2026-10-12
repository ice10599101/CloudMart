package com.cloudmart.product.dto;

/**
 * R08 订单行评价资格事实（与 mall-order ReviewEligibilityItem JSON 同构，
 * 跨服务只靠字段名契约映射，不共享实体）。
 */
public record OrderItemEligibility(
        Long orderItemId,
        Long orderId,
        Long productId,
        Long skuId,
        String productName,
        String skuAttributes
) {
}
