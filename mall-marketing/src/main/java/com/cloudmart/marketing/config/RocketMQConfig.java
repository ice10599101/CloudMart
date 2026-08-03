package com.cloudmart.marketing.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code marketing.events} exchange → {@code marketing-events} topic</li>
 *   <li>routing key {@code marketing.group.success} → tag {@code group-success}</li>
 *   <li>routing key {@code marketing.group.expired} → tag {@code group-expired}</li>
 *   <li>原 {@code marketing.group.expired} queue（自消费）→ consumer group {@code marketing-group-expired-cg}</li>
 * </ul>
 * 注意：{@code group-expired} 同时被 mall-marketing 与 mall-payment 消费，
 * 两个服务使用不同的 ConsumerGroup 以实现各自独立消费。
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String MARKETING_TOPIC = "marketing-events";

    public static final String MARKETING_TAG_GROUP_SUCCESS = "group-success";
    public static final String MARKETING_TAG_GROUP_EXPIRED = "group-expired";

    public static final String CG_MARKETING_GROUP_EXPIRED = "marketing-group-expired-cg";
}
