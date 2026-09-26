package com.cloudmart.pet.enums;

/**
 * 每日任务状态：IN_PROGRESS（进行中）→ COMPLETE（已完成待领奖）→ CLAIMED（已领奖）。
 */
public enum PetQuestStatus {
    /** 进行中 */
    IN_PROGRESS,
    /** 已完成待领奖 */
    COMPLETE,
    /** 已领奖 */
    CLAIMED,
    /** 已过期（超 72h 未领取） */
    EXPIRED,
    /** 已取消（B15：配置停用显式取消，不阻挡宝箱） */
    CANCELLED
}
