package com.cloudmart.wish.config;

/**
 * RocketMQ 拓扑常量定义。
 *
 * <p>心愿宇宙模块通过 RocketMQ 实现异步事件驱动：</p>
 * <ul>
 *   <li>{@code wish-events} topic → 心愿生命周期事件（创建/审核/还愿）</li>
 *   <li>Tag {@code wish-created} → 心愿创建事件（通知服务推送、统计服务聚合）</li>
 *   <li>Tag {@code wish-audited} → 审核结果事件（通知作者）</li>
 *   <li>Tag {@code wish-fulfilled} → 还愿事件（触发徽章发放、传承推送）</li>
 *   <li>Tag {@code wish-stat-sync} → 统计同步事件（异步更新 wish_user_stat）</li>
 * </ul>
 *
 * <p>消费者组命名：{@code wish-{role}-cg}，避免与其他模块冲突。</p>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    /** 心愿事件 topic */
    public static final String WISH_TOPIC = "wish-events";

    /** Tag：心愿创建 */
    public static final String WISH_TAG_CREATED = "wish-created";
    /** Tag：审核结果 */
    public static final String WISH_TAG_AUDITED = "wish-audited";
    /** Tag：还愿完成 */
    public static final String WISH_TAG_FULFILLED = "wish-fulfilled";
    /** Tag：统计同步 */
    public static final String WISH_TAG_STAT_SYNC = "wish-stat-sync";
    /** Tag：时间胶囊到期待开启（Sprint 2.4，mall-notification 消费推送） */
    public static final String WISH_TAG_CAPSULE_AVAILABLE = "capsule-available";
    /** Tag：预期管理 AI 引导（Sprint 2.5，mall-notification 消费推送 CHECKIN_REMINDER） */
    public static final String WISH_TAG_EXPECTED_GUIDE = "expected-guide";
    /** Tag：陪伴提醒（Sprint 2.5，mall-notification 消费推送 AI_REMINDER） */
    public static final String WISH_TAG_COMPANION_REMINDER = "companion-reminder";
    /** Tag：同路人互相提醒（Sprint 2.6，mall-notification 消费推送 AI_REMINDER） */
    public static final String WISH_TAG_SQUAD_REMIND = "squad-remind";
    /** Tag：小队成员变动通知（Sprint 2.6：被踢/解散，SYSTEM 站内信） */
    public static final String WISH_TAG_SQUAD_EVENT = "squad-event";

    /** 消费者组：统计同步 */
    public static final String CG_WISH_STAT_SYNC = "wish-stat-sync-cg";
    /** 消费者组：通知推送 */
    public static final String CG_WISH_NOTIFICATION = "wish-notification-cg";
}
