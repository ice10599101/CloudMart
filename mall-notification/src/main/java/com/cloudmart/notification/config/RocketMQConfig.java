package com.cloudmart.notification.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code order.events} exchange → {@code order-events} topic，订阅 tag {@code status-change}</li>
 *   <li>{@code community.events} exchange → {@code community-events} topic，订阅 tag {@code event}</li>
 * </ul>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String ORDER_TOPIC = "order-events";
    public static final String COMMUNITY_TOPIC = "community-events";

    public static final String ORDER_TAG_STATUS_CHANGE = "status-change";
    public static final String COMMUNITY_TAG_EVENT = "event";

    public static final String CG_NOTIFICATION_ORDER_STATUS = "notification-order-status-cg";
    public static final String CG_NOTIFICATION_COMMUNITY_EVENT = "notification-community-event-cg";
}
