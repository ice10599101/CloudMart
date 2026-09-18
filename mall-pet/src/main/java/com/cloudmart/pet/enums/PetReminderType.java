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
    /** 宠物饿了 */
    PET_HUNGRY,
    /** 社区动态聚合播报（评论/点赞/关注合并成一条） */
    PET_COMMUNITY_DIGEST
}
