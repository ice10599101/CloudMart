package com.cloudmart.payment.config;

/**
 * RocketMQ 拓扑常量定义（T01：仅保留 Outbox 投递使用的 payment-events topic；
 * 拼团过期退款消费者已删除，marketing topic 常量由 mall-marketing 自持）。
 *
 * <p>映射关系：{@code payment.events} exchange → {@code payment-events} topic；
 * routing key → tag。</p>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String PAYMENT_TOPIC = "payment-events";
    /** T11：售后事件发布到订单主题（mall-order 消费） */
    public static final String ORDER_TOPIC = "order-events";

    public static final String PAYMENT_TAG_RESULT = "result";
    public static final String PAYMENT_TAG_REFUND = "refund";
    /** T11：售后质检通过事件（order-events topic，mall-order 消费驱动自动退款） */
    public static final String TAG_AFTER_SALE = "after-sale";
}
