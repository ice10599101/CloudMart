package com.cloudmart.marketing.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code marketing.events} exchange → {@code marketing-events} topic</li>
 *   <li>成团事件 Outbox 发布 → tag {@code group-success}</li>
 * </ul>
 * 注意：T10 起 group-expired 消息已废弃——"成团后建单付款"模式下失败团
 * 释放预留权益（不产生退款事实），payment 侧退款消费者已随旧链路删除。
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String MARKETING_TOPIC = "marketing-events";

    public static final String MARKETING_TAG_GROUP_SUCCESS = "group-success";
}
