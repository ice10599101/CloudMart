package com.cloudmart.pet.constant;

/**
 * 社区宠物模块错误码常量。
 *
 * <p>所有错误码以 PET_ 前缀。HTTP 状态映射在 mall-common
 * {@code GlobalExceptionHandler.mapBusinessCodeToStatus} 中显式登记。</p>
 */
public final class PetErrorCodes {

    private PetErrorCodes() {}

    // --- 400 Bad Request ---
    public static final String PET_VALIDATION_ERROR = "PET_VALIDATION_ERROR";
    /** 宠物种类非法 */
    public static final String PET_SPECIES_INVALID = "PET_SPECIES_INVALID";
    /** 性格非法 */
    public static final String PET_PERSONALITY_INVALID = "PET_PERSONALITY_INVALID";
    /** 外观参数非法 */
    public static final String PET_APPEARANCE_INVALID = "PET_APPEARANCE_INVALID";
    /** 对战模式非法 */
    public static final String PET_BATTLE_MODE_INVALID = "PET_BATTLE_MODE_INVALID";
    /** 聊天内容为空或超长 */
    public static final String PET_CHAT_MESSAGE_INVALID = "PET_CHAT_MESSAGE_INVALID";

    // --- 403 Forbidden ---
    /** 非宠物主人，禁止操作 */
    public static final String PET_NOT_OWNER = "PET_NOT_OWNER";
    /** 宠物主人未公开宠物资料 */
    public static final String PET_NOT_PUBLIC = "PET_NOT_PUBLIC";
    public static final String PET_FORBIDDEN = "PET_FORBIDDEN";

    // --- 404 Not Found ---
    public static final String PET_NOT_FOUND = "PET_NOT_FOUND";
    public static final String PET_ACTIVITY_NOT_FOUND = "PET_ACTIVITY_NOT_FOUND";
    public static final String PET_BATTLE_NOT_FOUND = "PET_BATTLE_NOT_FOUND";
    public static final String PET_JOB_NOT_FOUND = "PET_JOB_NOT_FOUND";
    public static final String PET_STUDY_NOT_FOUND = "PET_STUDY_NOT_FOUND";
    public static final String PET_ACHIEVEMENT_NOT_FOUND = "PET_ACHIEVEMENT_NOT_FOUND";

    // --- 409 Conflict ---
    /** 已有宠物，重复领养 */
    public static final String PET_ALREADY_EXISTS = "PET_ALREADY_EXISTS";
    /** 宠物正在进行其他活动（打工/读书/捞瓶互斥） */
    public static final String PET_ACTIVITY_CONFLICT = "PET_ACTIVITY_CONFLICT";
    /** 任务未完成，不可领取 */
    public static final String PET_ACTIVITY_NOT_FINISHED = "PET_ACTIVITY_NOT_FINISHED";
    /** 奖励已领取（幂等第二次返回） */
    public static final String PET_ACTIVITY_ALREADY_CLAIMED = "PET_ACTIVITY_ALREADY_CLAIMED";
    /** 捞瓶任务未完成或冷却中 */
    public static final String PET_BOTTLE_COOLDOWN = "PET_BOTTLE_COOLDOWN";
    /** 精力不足 */
    public static final String PET_ENERGY_INSUFFICIENT = "PET_ENERGY_INSUFFICIENT";
    /** 饥饿度过低无法行动 */
    public static final String PET_HUNGER_TOO_LOW = "PET_HUNGER_TOO_LOW";
    /** 状态已满（清洁度满不能再洗等） */
    public static final String PET_STATE_FULL = "PET_STATE_FULL";
    /** 等级不满足要求 */
    public static final String PET_LEVEL_REQUIRED = "PET_LEVEL_REQUIRED";
    /** 对战状态冲突（重复 accept/decline、非防守方操作等） */
    public static final String PET_BATTLE_CONFLICT = "PET_BATTLE_CONFLICT";
    /** 对战已被处理过 */
    public static final String PET_BATTLE_ALREADY_HANDLED = "PET_BATTLE_ALREADY_HANDLED";
    /** 不能挑战自己的宠物 */
    public static final String PET_BATTLE_SELF_CHALLENGE = "PET_BATTLE_SELF_CHALLENGE";
    /** 对手宠物不存在或未公开 */
    public static final String PET_BATTLE_OPPONENT_INVALID = "PET_BATTLE_OPPONENT_INVALID";
    /** 改名冷却中（30 天一次） */
    public static final String PET_RENAME_COOLDOWN = "PET_RENAME_COOLDOWN";

    // --- 429 Too Many Requests ---
    /** 今日聊天次数已达上限 */
    public static final String PET_AI_RATE_LIMITED = "PET_AI_RATE_LIMITED";
    /** 今日互动次数已达上限 */
    public static final String PET_INTERACTION_RATE_LIMITED = "PET_INTERACTION_RATE_LIMITED";

    // --- 503 Service Unavailable ---
    /** AI 服务不可用（聊天场景已降级模板；该码供显式要求 AI 的场景使用） */
    public static final String PET_AI_UNAVAILABLE = "PET_AI_UNAVAILABLE";
}
