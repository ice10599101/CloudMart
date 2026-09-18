package com.cloudmart.pet.enums;

/**
 * 活动状态机：IN_PROGRESS → COMPLETED → CLAIMED；
 * COMPLETED 超 72h 未领取 → EXPIRED（奖励作废）。
 * 领取用条件 UPDATE CAS 流转，幂等：重复领取命中 0 行报 PET_ACTIVITY_ALREADY_CLAIMED。
 */
public enum PetActivityStatus {
    IN_PROGRESS,
    COMPLETED,
    CLAIMED,
    EXPIRED
}
