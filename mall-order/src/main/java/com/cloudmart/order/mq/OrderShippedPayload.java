package com.cloudmart.order.mq;

/**
 * ORDER_SHIPPED 事件载荷（mall-wms 出库发货事实）。
 *
 * @param orderId    订单 ID（必填）
 * @param shipmentId 物流包裹 ID（必填：缺失说明不是真实出库事实）
 * @param carrier    承运商（审计/展示）
 * @param trackingNo 承运商运单号（审计/展示）
 */
public record OrderShippedPayload(
        Long orderId,
        Long shipmentId,
        String carrier,
        String trackingNo
) {
}
