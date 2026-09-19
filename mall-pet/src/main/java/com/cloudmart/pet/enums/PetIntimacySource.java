package com.cloudmart.pet.enums;

/**
 * 亲密度来源（决定每次互动加多少，数值集中在 {@code PetProperties.Intimacy}）。
 *
 * <p>与每日任务口径的区别：亲密度是"长期关系"数值（只增不减），
 * 每日任务关注"当天是否做了"；两者共用同一批业务埋点。</p>
 */
public enum PetIntimacySource {
    /** 喂食 */
    FEED,
    /** 玩耍 */
    PLAY,
    /** 清洁 */
    CLEAN,
    /** 休息 */
    REST,
    /** 聊天 */
    CHAT,
    /** 打工 / 职业工作 */
    WORK,
    /** 读书 */
    STUDY,
    /** 捞瓶 */
    BOTTLE,
    /** 对战 */
    BATTLE,
    /** 串门 / 好友互访 */
    VISIT,
    /** 家园（每日首次进入自己房间） */
    ROOM,
    /** 留言互动（留言或收到留言） */
    WALL,
    /** 每日任务领奖 */
    QUEST,
    /** 陪伴时长（心跳按分钟累计换算） */
    COMPANION
}
