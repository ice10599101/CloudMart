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
    /** 皮肤不适用于当前宠物种类 */
    public static final String PET_SKIN_SPECIES_MISMATCH = "PET_SKIN_SPECIES_MISMATCH";

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
    /** 商城物品不存在或已下架 */
    public static final String PET_ITEM_NOT_FOUND = "PET_ITEM_NOT_FOUND";
    /** 技能不存在或已下架 */
    public static final String PET_SKILL_NOT_FOUND = "PET_SKILL_NOT_FOUND";
    /** 进化配置不存在或已停用 */
    public static final String PET_EVOLUTION_NOT_FOUND = "PET_EVOLUTION_NOT_FOUND";
    /** 社区活动不存在或已下架 */
    public static final String PET_EVENT_NOT_FOUND = "PET_EVENT_NOT_FOUND";

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
    /** 宠物数量已达上限（多宠物） */
    public static final String PET_PET_LIMIT_REACHED = "PET_PET_LIMIT_REACHED";
    /** 已拥有该物品（重复购买） */
    public static final String PET_ITEM_ALREADY_OWNED = "PET_ITEM_ALREADY_OWNED";
    /** 尚未拥有该物品（先购买再装备/穿戴） */
    public static final String PET_ITEM_NOT_OWNED = "PET_ITEM_NOT_OWNED";
    /** 技能已学会（重复学习） */
    public static final String PET_SKILL_ALREADY_LEARNED = "PET_SKILL_ALREADY_LEARNED";
    /** 缺少技能书，需先在商城购买 */
    public static final String PET_SKILL_BOOK_REQUIRED = "PET_SKILL_BOOK_REQUIRED";
    /** 进化条件不满足（等级/阶段） */
    public static final String PET_EVOLUTION_REQUIRED = "PET_EVOLUTION_REQUIRED";
    /** 已达到最高进化阶段 */
    public static final String PET_EVOLUTION_MAX = "PET_EVOLUTION_MAX";
    /** 不能给自己串门 */
    public static final String PET_VISIT_SELF = "PET_VISIT_SELF";
    /** 串门冷却中（同一邻居每日一次） */
    public static final String PET_VISIT_COOLDOWN = "PET_VISIT_COOLDOWN";
    /** 串门时宠物精力不足 */
    public static final String PET_VISIT_ENERGY_INSUFFICIENT = "PET_VISIT_ENERGY_INSUFFICIENT";
    /** 活动目标未完成，不可领奖 */
    public static final String PET_EVENT_NOT_FINISHED = "PET_EVENT_NOT_FINISHED";
    /** 活动奖励已领取（幂等第二次返回） */
    public static final String PET_EVENT_ALREADY_CLAIMED = "PET_EVENT_ALREADY_CLAIMED";
    /** 活动已结束 */
    public static final String PET_EVENT_ENDED = "PET_EVENT_ENDED";
    /** 职业不存在或已停招 */
    public static final String PET_CAREER_NOT_FOUND = "PET_CAREER_NOT_FOUND";
    /** 尚未入职该职业（先入职再工作/晋升） */
    public static final String PET_CAREER_REQUIRED = "PET_CAREER_REQUIRED";
    /** 入职条件不满足（等级/智力） */
    public static final String PET_CAREER_LOCKED = "PET_CAREER_LOCKED";
    /** 晋升条件不满足（工作次数/等级/星光） */
    public static final String PET_CAREER_PROMOTE_REQUIRED = "PET_CAREER_PROMOTE_REQUIRED";
    /** 已是该职业路线最高阶 */
    public static final String PET_CAREER_MAX_TIER = "PET_CAREER_MAX_TIER";
    /** 关系不存在或不可操作 */
    public static final String PET_RELATION_NOT_FOUND = "PET_RELATION_NOT_FOUND";
    /** 不能和自己的宠物建立关系 */
    public static final String PET_RELATION_SELF = "PET_RELATION_SELF";
    /** 关系已存在（重复申请/重复建立） */
    public static final String PET_RELATION_EXISTS = "PET_RELATION_EXISTS";
    /** 关系数量已达上限 */
    public static final String PET_RELATION_LIMIT = "PET_RELATION_LIMIT";
    /** 该关系类型独占（情侣只能有一段） */
    public static final String PET_RELATION_EXCLUSIVE = "PET_RELATION_EXCLUSIVE";
    /** 关系申请已失效（非待确认状态） */
    public static final String PET_RELATION_NOT_PENDING = "PET_RELATION_NOT_PENDING";
    /** 不能加自己为好友 */
    public static final String PET_FRIEND_SELF = "PET_FRIEND_SELF";
    /** 好友关系已存在或申请中 */
    public static final String PET_FRIEND_EXISTS = "PET_FRIEND_EXISTS";
    /** 好友数量已达上限 */
    public static final String PET_FRIEND_LIMIT = "PET_FRIEND_LIMIT";
    /** 好友关系不存在 */
    public static final String PET_FRIEND_NOT_FOUND = "PET_FRIEND_NOT_FOUND";
    /** 还没有自己的房间（先进入一次家园） */
    public static final String PET_ROOM_NOT_FOUND = "PET_ROOM_NOT_FOUND";
    /** 对方家园未公开 */
    public static final String PET_ROOM_PRIVATE = "PET_ROOM_PRIVATE";
    /** 房间坐标非法（超出网格） */
    public static final String PET_ROOM_POS_INVALID = "PET_ROOM_POS_INVALID";
    /** 该格子已被占用 */
    public static final String PET_ROOM_POS_OCCUPIED = "PET_ROOM_POS_OCCUPIED";
    /** 家具不存在或已下架 */
    public static final String PET_FURNITURE_NOT_FOUND = "PET_FURNITURE_NOT_FOUND";
    /** 尚未拥有该家具 */
    public static final String PET_FURNITURE_NOT_OWNED = "PET_FURNITURE_NOT_OWNED";
    /** 主题家具分类不匹配（墙纸/地板） */
    public static final String PET_FURNITURE_THEME_INVALID = "PET_FURNITURE_THEME_INVALID";
    /** 留言内容为空或超长 */
    public static final String PET_WALL_MESSAGE_INVALID = "PET_WALL_MESSAGE_INVALID";
    /** 留言不存在或已被删除 */
    public static final String PET_WALL_MESSAGE_NOT_FOUND = "PET_WALL_MESSAGE_NOT_FOUND";
    /** 无权操作该留言（非作者/非主人） */
    public static final String PET_WALL_FORBIDDEN = "PET_WALL_FORBIDDEN";
    /** 留言/点赞过于频繁 */
    public static final String PET_WALL_RATE_LIMITED = "PET_WALL_RATE_LIMITED";
    /** 每日任务不存在或未生成 */
    public static final String PET_QUEST_NOT_FOUND = "PET_QUEST_NOT_FOUND";
    /** 任务尚未完成，不可领奖 */
    public static final String PET_QUEST_NOT_FINISHED = "PET_QUEST_NOT_FINISHED";
    /** 任务奖励已领取 */
    public static final String PET_QUEST_ALREADY_CLAIMED = "PET_QUEST_ALREADY_CLAIMED";
    /** 全清宝箱条件未达成 */
    public static final String PET_QUEST_CHEST_NOT_READY = "PET_QUEST_CHEST_NOT_READY";
    /** 全清宝箱已领取 */
    public static final String PET_QUEST_CHEST_CLAIMED = "PET_QUEST_CHEST_CLAIMED";
    /** 陪伴心跳参数非法 */
    public static final String PET_COMPANION_INVALID = "PET_COMPANION_INVALID";

    // --- B01/B02 并发与结算（§6.4 错误码登记） ---
    /** 宠物状态版本冲突（并发写未命中，可重试；不产生任何奖励） */
    public static final String PET_STATE_CONFLICT = "PET_STATE_CONFLICT";
    /** 星光结算结果未知/处理中（业务成功但外部待结算；按原请求重试幂等，禁止换单号） */
    public static final String PET_SETTLEMENT_PENDING = "PET_SETTLEMENT_PENDING";
    /** 业务操作记录不存在（结果未知，可按原单查询/重试） */
    public static final String PET_OPERATION_NOT_FOUND = "PET_OPERATION_NOT_FOUND";
    /** 重复请求与原操作内容冲突（同键不同业务实例） */
    public static final String PET_OPERATION_CONFLICT = "PET_OPERATION_CONFLICT";
    /** 每日收益额度已耗尽（可执行无收益互动，不推进奖励） */
    public static final String PET_QUOTA_EXHAUSTED = "PET_QUOTA_EXHAUSTED";
    /** 用户交互互斥位被占用（长期活动/小游戏进行中，禁止并行开始另一项） */
    public static final String PET_USER_BUSY = "PET_USER_BUSY";
    /** 家具已摆放（B13：每种家具每宠物至多一个摆放实例） */
    public static final String PET_FURNITURE_ALREADY_PLACED = "PET_FURNITURE_ALREADY_PLACED";
    /** 已被屏蔽/拒收（B14：屏蔽名单生效） */
    public static final String PET_BLOCKED = "PET_BLOCKED";
    /** 对象已私密/不可访问 */
    public static final String PET_PERMISSION_DENIED = "PET_PERMISSION_DENIED";

    // --- 429 Too Many Requests ---
    /** 今日聊天次数已达上限 */
    public static final String PET_AI_RATE_LIMITED = "PET_AI_RATE_LIMITED";
    /** 今日互动次数已达上限 */
    public static final String PET_INTERACTION_RATE_LIMITED = "PET_INTERACTION_RATE_LIMITED";

    // --- 503 Service Unavailable ---
    /** AI 服务不可用（聊天场景已降级模板；该码供显式要求 AI 的场景使用） */
    public static final String PET_AI_UNAVAILABLE = "PET_AI_UNAVAILABLE";
}
