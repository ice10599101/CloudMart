package com.cloudmart.wms.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code order.events} exchange → {@code order-events} topic</li>
 *   <li>routing key {@code order.paid} → tag {@code paid}</li>
 *   <li>原 {@code wms.order.paid} queue → consumer group {@code wms-order-paid-cg}</li>
 * </ul>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String ORDER_TOPIC = "order-events";
    public static final String ORDER_TAG_PAID = "paid";

    public static final String CG_WMS_ORDER_PAID = "wms-order-paid-cg";
}
