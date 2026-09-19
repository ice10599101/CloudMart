package com.cloudmart.pet.enums;

/**
 * 宠物提醒子类型（落 notifications.biz_type；type 统一为 PET，前端按此过滤展示）。
 * 优先级映射（原文档 §30）：P0=私信类/活动到期、P1=评论/捞瓶/任务完成、P2=点赞/动态；
 * 一期主动消息按触发器频控聚合，优先级随文案前缀区分。
 */
public enum PetReminderType {
    /** 打工完成可领取 */
    PET_WORK_COMPLETED,
    /** 读书完成可领取 */
    PET_STUDY_COMPLETED,
    /** 宠物捞到漂流瓶 */
    PET_BOTTLE_CAUGHT,
    /** 战斗结束（防守方收到挑战结果） */
    PET_BATTLE_FINISHED,
    /** 收到新的对战挑战 */
    PET_BATTLE_CHALLENGE,
    /** 宠物升级 */
    PET_LEVEL_UP,
    /** 成就达成 */
    PET_ACHIEVEMENT,
    /** 每日问候（≤1 次/日） */
    PET_DAILY_GREETING,
    /** 长时间未陪伴 */
    PET_LONG_ABSENT,
    /** 私信提醒（原文档 §28.3，P0） */
    PET_MESSAGE,
    /** 社区活动即将结束（原文档 §28.6，P0） */
    PET_ACTIVITY_ENDING,
    /** 宠物饿了 */
    PET_HUNGRY,
    /** 社区动态聚合播报（评论/点赞/关注合并成一条） */
    PET_COMMUNITY_DIGEST,
    /** 宠物进化成功（原文档 §89 宠物进化） */
    PET_EVOLVED,
    /** 有宠物来串门（原文档 §1.1 宠物串门） */
    PET_VISIT,
    /** 社区宠物活动达成可领奖（原文档 §89 社区宠物活动） */
    PET_EVENT_READY,
    /** 收到宠物关系申请（三期：情侣/闺蜜/兄弟/死党） */
    PET_RELATION_REQUEST,
    /** 宠物关系已建立 */
    PET_RELATION_ACCEPTED,
    /** 收到好友申请 */
    PET_FRIEND_REQUEST,
    /** 好友来访 / 好友互访回礼 */
    PET_FRIEND_VISIT,
    /** 留言墙收到留言 / 主人回复 */
    PET_WALL_MESSAGE,
    /** 每日任务全部完成可领宝箱 */
    PET_DAILY_QUEST_READY,
    /** 与主人的亲密度升级 */
    PET_INTIMACY_LEVEL_UP,
    /** 职业晋升成功 */
    PET_CAREER_PROMOTED,
    /** 有访客来过家园 */
    PET_HOME_VISIT
}
