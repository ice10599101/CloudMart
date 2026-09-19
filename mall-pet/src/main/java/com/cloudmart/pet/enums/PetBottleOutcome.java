package com.cloudmart.pet.enums;

/**
 * 宠物捞瓶结果（pet_bottle_record.outcome）。
 */
public enum PetBottleOutcome {
    /** 成功捞起（bottleId 指向 wish_drift_bottle） */
    CAUGHT,
    /** 空手而归（成功率未中或海里无瓶） */
    EMPTY,
    /** 心愿服务降级未捞起（可重试领取） */
    FAILED
}
