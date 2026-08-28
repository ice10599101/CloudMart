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
    public static final String WISH_TOPIC = "wish-events";

    public static final String ORDER_TAG_STATUS_CHANGE = "status-change";
    public static final String COMMUNITY_TAG_EVENT = "event";
    public static final String WISH_TAG_CAPSULE_AVAILABLE = "capsule-available";
    /** Tag：预期管理 AI 引导（Sprint 2.5，推送 CHECKIN_REMINDER 站内信，含 3 选项） */
    public static final String WISH_TAG_EXPECTED_GUIDE = "expected-guide";
    /** Tag：陪伴提醒（Sprint 2.5，推送 AI_REMINDER 站内信，每日 1 条） */
    public static final String WISH_TAG_COMPANION_REMINDER = "companion-reminder";
    /** Tag：同路人互相提醒（Sprint 2.6，推送 AI_REMINDER 站内信） */
    public static final String WISH_TAG_SQUAD_REMIND = "squad-remind";
    /** Tag：小队成员变动（Sprint 2.6：被踢/解散，SYSTEM 站内信） */
    public static final String WISH_TAG_SQUAD_EVENT = "squad-event";
    /** Tag：还愿传承推送（Sprint 2.7，WISH_FULFILL 站内信，"你的同愿实现了"） */
    public static final String WISH_TAG_LEGACY_PUSH = "legacy-push";

    public static final String CG_NOTIFICATION_ORDER_STATUS = "notification-order-status-cg";
    public static final String CG_NOTIFICATION_COMMUNITY_EVENT = "notification-community-event-cg";
    public static final String CG_NOTIFICATION_WISH_EVENT = "notification-wish-event-cg";
    public static final String CG_NOTIFICATION_AI_REMINDER = "notification-ai-reminder-cg";
}
