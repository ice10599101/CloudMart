package com.cloudmart.wish.constant;

/**
 * 心愿宇宙模块错误码常量。
 *
 * <p>所有错误码以 WISH_ 前缀，对应文档 2.1 节 errors 定义。
 * HTTP 状态映射在 {@code GlobalExceptionHandler} 中配置。</p>
 */
public final class WishErrorCodes {
    public static final String WISH_CONFIRM_CODE_INVALID = "WISH_CONFIRM_CODE_INVALID";
    public static final String WISH_DELETION_PENDING = "WISH_DELETION_PENDING";
    public static final String WISH_DELETION_EXECUTED = "WISH_DELETION_EXECUTED";

    private WishErrorCodes() {}

    // --- 400 Bad Request ---
    public static final String WISH_TITLE_REQUIRED = "WISH_TITLE_REQUIRED";
    public static final String WISH_DESCRIPTION_REQUIRED = "WISH_DESCRIPTION_REQUIRED";
    public static final String WISH_CATEGORY_INVALID = "WISH_CATEGORY_INVALID";
    public static final String WISH_VISIBILITY_INVALID = "WISH_VISIBILITY_INVALID";
    public static final String WISH_VALIDATION_ERROR = "WISH_VALIDATION_ERROR";
    /** 时间胶囊开启时间不能早于当前时间（文档 2.7 errors） */
    public static final String WISH_OPEN_AT_PAST = "WISH_OPEN_AT_PAST";
    public static final String WISH_INTERACTION_TYPE_INVALID = "WISH_INTERACTION_TYPE_INVALID";
    public static final String WISH_INTERACTION_NOT_FOUND = "WISH_INTERACTION_NOT_FOUND";

    // --- 403 Forbidden ---
    public static final String WISH_NOT_AUTHOR = "WISH_NOT_AUTHOR";
    public static final String WISH_RESTRICTED = "WISH_RESTRICTED";
    public static final String WISH_FORBIDDEN = "WISH_FORBIDDEN";
    /** 未同意 AI 数据处理协议（文档 2.11 / 39.8） */
    public static final String WISH_CONSENT_REQUIRED = "WISH_CONSENT_REQUIRED";

    // --- 404 Not Found ---
    public static final String WISH_NOT_FOUND = "WISH_NOT_FOUND";
    public static final String WISH_CATEGORY_NOT_FOUND = "WISH_CATEGORY_NOT_FOUND";
    /** 还愿记录不存在（心愿未还愿或已撤回还愿故事） */
    public static final String WISH_FULFILLMENT_NOT_FOUND = "WISH_FULFILLMENT_NOT_FOUND";

    // --- 409 Conflict ---
    public static final String WISH_STATUS_CONFLICT = "WISH_STATUS_CONFLICT";
    public static final String WISH_ALREADY_INTERACTED = "WISH_ALREADY_INTERACTED";
    public static final String WISH_ALREADY_CHECKIN_TODAY = "WISH_ALREADY_CHECKIN_TODAY";
    public static final String WISH_VERSION_CONFLICT = "WISH_VERSION_CONFLICT";
    public static final String WISH_NOT_FULFILLABLE = "WISH_NOT_FULFILLABLE";
    /** 时间胶囊未到期待开启（文档 2.7 errors） */
    public static final String WISH_CAPSULE_NOT_AVAILABLE = "WISH_CAPSULE_NOT_AVAILABLE";
    /** 虚拟资产仍有用户持有，不可物理删除（管理端，改用下架） */
    public static final String WISH_ASSET_IN_USE = "WISH_ASSET_IN_USE";

    // --- 402 Payment Required ---
    public static final String WISH_STARLIGHT_INSUFFICIENT = "WISH_STARLIGHT_INSUFFICIENT";

    // --- 429 Too Many Requests ---
    public static final String WISH_RATE_LIMITED = "WISH_RATE_LIMITED";
    /** AI 功能调用频次达上限（文档 30.3：树洞 10 次/日） */
    public static final String WISH_AI_RATE_LIMITED = "WISH_AI_RATE_LIMITED";

    // 徽章管理（Sprint 1.8）
    public static final String BADGE_NOT_FOUND = "BADGE_NOT_FOUND";
    public static final String BADGE_CODE_DUPLICATED = "BADGE_CODE_DUPLICATED";
    public static final String BADGE_CONDITION_INVALID = "BADGE_CONDITION_INVALID";

    // 生命树环境管理（Sprint 2.2）
    /** 特殊事件不存在 */
    public static final String TREE_SPECIAL_EVENT_NOT_FOUND = "TREE_SPECIAL_EVENT_NOT_FOUND";
    /** 环境配置不存在 */
    public static final String TREE_ENV_CONFIG_NOT_FOUND = "TREE_ENV_CONFIG_NOT_FOUND";
    /** 环境配置 code 重复（唯一索引兜底前的预查） */
    public static final String TREE_ENV_CONFIG_CODE_DUPLICATED = "TREE_ENV_CONFIG_CODE_DUPLICATED";
    /** 环境配置 visual 非法 JSON */
    public static final String TREE_ENV_VISUAL_INVALID = "TREE_ENV_VISUAL_INVALID";

    // 背景音乐曲库（Sprint 2.3）
    /** BGM 歌曲不存在 */
    public static final String BGM_SONG_NOT_FOUND = "BGM_SONG_NOT_FOUND";
    /** BGM 音频地址非法（须为 http(s) 直链） */
    public static final String BGM_SONG_URL_INVALID = "BGM_SONG_URL_INVALID";

    // AI 心愿助手（Sprint 2.5）
    /** AI 拆解目标不存在（文档 2.11 PUT /ai/goals/{goalId}） */
    public static final String WISH_AI_GOAL_NOT_FOUND = "WISH_AI_GOAL_NOT_FOUND";
    /** 目标状态流转非法（如终态再变更） */
    public static final String WISH_AI_GOAL_STATUS_INVALID = "WISH_AI_GOAL_STATUS_INVALID";
    /** Prompt 模板不存在（管理端） */
    public static final String WISH_AI_PROMPT_NOT_FOUND = "WISH_AI_PROMPT_NOT_FOUND";
    /** 通知类型/渠道非法（文档 2.14） */
    public static final String WISH_NOTIFICATION_TYPE_INVALID = "WISH_NOTIFICATION_TYPE_INVALID";
    /** 年度报告参数非法（年份越界） */
    public static final String WISH_ANNUAL_REPORT_INVALID = "WISH_ANNUAL_REPORT_INVALID";

    // 同愿匹配 + 监督小队（Sprint 2.6，文档 2.8）
    /** 小组不存在或已关闭 */
    public static final String WISH_GROUP_NOT_FOUND = "WISH_GROUP_NOT_FOUND";
    /** 小组已满员（并发加入 CAS 兜底，文档验收：仅 1 人成功） */
    public static final String WISH_GROUP_FULL = "WISH_GROUP_FULL";
    /** 已是该小组 ACTIVE 成员 */
    public static final String WISH_ALREADY_MEMBER = "WISH_ALREADY_MEMBER";
    /** 同一关键词下已有进行中的小组（一人一组防占坑） */
    public static final String WISH_GROUP_KEYWORD_DUPLICATED = "WISH_GROUP_KEYWORD_DUPLICATED";
    /** 被踢 24h 冷却期内不可加入同关键词小组 */
    public static final String WISH_KICKED_COOLDOWN = "WISH_KICKED_COOLDOWN";
    /** 非组长无权执行该操作（踢人/解散） */
    public static final String WISH_GROUP_LEADER_REQUIRED = "WISH_GROUP_LEADER_REQUIRED";
    /** 匹配配置值非法（权重越界等，管理端） */
    public static final String WISH_MATCH_CONFIG_INVALID = "WISH_MATCH_CONFIG_INVALID";

    // 还愿传承 + 内容生态 + 排行榜（Sprint 2.7，文档 2.8/2.9）
    /** 心愿未还愿，不可发起传承（文档 2.8：409 WISH_NOT_FULFILLED） */
    public static final String WISH_NOT_FULFILLED = "WISH_NOT_FULFILLED";
    /** 该还愿已发起过传承（一次还愿一次传承） */
    public static final String WISH_ALREADY_INHERITED = "WISH_ALREADY_INHERITED";
    /** 星火设置并发冲突（条件 UPDATE 未命中且非幂等场景，409） */
    public static final String WISH_SPARK_CONFLICT = "WISH_SPARK_CONFLICT";
    /** 排行榜榜单类型非法 */
    public static final String WISH_LEADERBOARD_TYPE_INVALID = "WISH_LEADERBOARD_TYPE_INVALID";
    /** 排行榜配置值非法（管理端） */
    public static final String WISH_LEADERBOARD_CONFIG_INVALID = "WISH_LEADERBOARD_CONFIG_INVALID";

    // --- 503 Service Unavailable ---
    /** AI 服务不可用（重试后仍失败，文档 30.1/30.3） */
    public static final String WISH_AI_UNAVAILABLE = "WISH_AI_UNAVAILABLE";
}
