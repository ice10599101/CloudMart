package com.cloudmart.pet.config;

/**
 * RocketMQ 拓扑常量定义（社区宠物模块）。
 *
 * <p>生产：{@code pet-events} topic（tag kebab-case，通知类事件由 mall-notification
 * 消费落 notifications 表 + WebSocket 推送）。</p>
 * <p>消费：{@code community-events: event}（自建消费组维护宠物上下文计数器，
 * 供宠物 AI 上下文/社区播报使用；通知落库仍由 mall-notification 自己的消费组完成，
 * 两组互不影响——RocketMQ 集群模式每组独立位点）。</p>
 */
public final class RocketMQConfig {

    private RocketMQConfig() {
    }

    /** 宠物事件 topic（mall-notification 消费推送） */
    public static final String PET_TOPIC = "pet-events";

    /** Tag：打工完成（可领取奖励提醒） */
    public static final String PET_TAG_WORK_COMPLETED = "work-completed";
    /** Tag：读书完成 */
    public static final String PET_TAG_STUDY_COMPLETED = "study-completed";
    /** Tag：捞瓶完成（捞到漂流瓶提醒） */
    public static final String PET_TAG_BOTTLE_CAUGHT = "bottle-caught";
    /** Tag：战斗结束（挑战结果提醒） */
    public static final String PET_TAG_BATTLE_FINISHED = "battle-finished";
    /** Tag：宠物升级 */
    public static final String PET_TAG_LEVEL_UP = "level-up";
    /** Tag：主动消息/通用宠物提醒（早安/社区播报/饥饿等） */
    public static final String PET_TAG_PROACTIVE = "proactive";
    /** Tag：成就达成 */
    public static final String PET_TAG_ACHIEVEMENT = "achievement";
    /** Tag：宠物进化（原文档 §89 宠物进化） */
    public static final String PET_TAG_EVOLVED = "evolved";
    /** Tag：宠物串门（原文档 §1.1 宠物串门） */
    public static final String PET_TAG_VISIT = "visit";
    /** Tag：社区宠物活动达成（原文档 §89 社区宠物活动） */
    public static final String PET_TAG_EVENT_READY = "event-ready";
    /** Tag：宠物关系（申请/建立，三期） */
    public static final String PET_TAG_RELATION = "relation";
    /** Tag：宠物好友（申请/互访，三期） */
    public static final String PET_TAG_FRIEND = "friend";
    /** Tag：留言墙（收到留言/主人回复，三期） */
    public static final String PET_TAG_WALL = "wall";
    /** Tag：每日任务（全清宝箱可领，三期） */
    public static final String PET_TAG_DAILY_QUEST = "daily-quest";
    /** Tag：亲密度升级（三期） */
    public static final String PET_TAG_INTIMACY = "intimacy";
    /** Tag：职业晋升（三期） */
    public static final String PET_TAG_CAREER = "career";
    /** Tag：家园来访（三期） */
    public static final String PET_TAG_HOME_VISIT = "home-visit";

    /** 社区事件 topic（mall-community 生产，mall-notification 与 mall-pet 各自消费） */
    public static final String COMMUNITY_TOPIC = "community-events";
    /** Tag：社区通用事件（LIKE/COMMENT/FOLLOW/...，与 mall-community CommunityEventProducer 对齐） */
    public static final String COMMUNITY_TAG_EVENT = "event";

    /** 消费者组：宠物上下文社区事件计数（不与 mall-notification 的组冲突） */
    public static final String CG_PET_COMMUNITY_EVENT = "pet-community-event-cg";
}
