package com.cloudmart.community.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>映射关系：
 * <ul>
 *   <li>{@code community.events} exchange → {@code community-events} topic</li>
 *   <li>routing key {@code community.event} → tag {@code event}（社区互动通知）</li>
 *   <li>routing key {@code community.like-times} → tag {@code like-times}（点赞数异步同步）</li>
 * </ul>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    public static final String COMMUNITY_TOPIC = "community-events";
    public static final String COMMUNITY_TAG_EVENT = "event";
    public static final String COMMUNITY_TAG_LIKE_TIMES = "like-times";

    public static final String CG_COMMUNITY_LIKE_TIMES = "community-like-times-cg";
}
