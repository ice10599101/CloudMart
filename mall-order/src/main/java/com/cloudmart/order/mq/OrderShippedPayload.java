package com.cloudmart.order.mq;

/** ORDER_SHIPPED 事件载荷（mall-wms 出库发货事实）。@param orderId 订单 ID（必填） */
public record OrderShippedPayload(
        Long orderId
) {
}
