package com.cloudmart.pet.enums;

/**
 * 宠物关系状态机：PENDING（待对方主人确认）→ ACTIVE（已建立）/ REJECTED（已拒绝）；
 * ACTIVE 可再流转为 DISSOLVED（任一方解除）。
 */
public enum PetRelationStatus {
    /** 待确认 */
    PENDING,
    /** 已建立 */
    ACTIVE,
    /** 已拒绝 */
    REJECTED,
    /** 已解除 */
    DISSOLVED
}
