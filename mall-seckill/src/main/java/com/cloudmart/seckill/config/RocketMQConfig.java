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
}
