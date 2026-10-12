package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * R08 订单行评价资格事实：由 mall-order 权威返回订单行真实归属，
 * mall-product 据此校验评价请求的 orderId/productId/skuId 不得伪造或交叉错配。
 *
 * @param orderItemId 订单行 ID（评价的唯一业务锚点）
 * @param orderId     所属订单 ID
 * @param productId   行商品 ID
 * @param skuId       行 SKU ID
 * @param productName  商品名（评价表单回显快照用）
 * @param skuAttributes SKU 销售属性（可能为空）
 */
@Schema(description = "订单行评价资格事实")
public record ReviewEligibilityItem(
        Long orderItemId,
        Long orderId,
        Long productId,
        Long skuId,
        String productName,
        String skuAttributes
) {
}
