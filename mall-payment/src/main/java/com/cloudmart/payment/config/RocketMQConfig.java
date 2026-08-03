package com.cloudmart.payment.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code payment.events} exchange → {@code payment-events} topic</li>
 *   <li>{@code marketing.events} exchange → {@code marketing-events} topic</li>
 *   <li>routing key → tag</li>
 * </ul>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String PAYMENT_TOPIC = "payment-events";
    public static final String MARKETING_TOPIC = "marketing-events";

    public static final String PAYMENT_TAG_RESULT = "result";
    public static final String PAYMENT_TAG_REFUND = "refund";
    public static final String MARKETING_TAG_GROUP_EXPIRED = "group-expired";

    public static final String CG_PAYMENT_GROUP_EXPIRED = "payment-group-expired-cg";
}
