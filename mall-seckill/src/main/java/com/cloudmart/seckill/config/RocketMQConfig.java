package com.cloudmart.seckill.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code seckill.events} exchange → {@code seckill-events} topic</li>
 *   <li>routing key {@code seckill.order} → tag {@code order}</li>
 * </ul>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String SECKILL_TOPIC = "seckill-events";
    public static final String SECKILL_TAG_ORDER = "order";

    /** T09：订单侧结果回写事件（order-events Outbox 发布，本服务 Inbox 消费落终态） */
    public static final String ORDER_TOPIC = "order-events";
    public static final String ORDER_TAG_SECKILL_RESULT = "seckill-result";
    public static final String CG_SECKILL_RESULT = "cg-seckill-result";
}
