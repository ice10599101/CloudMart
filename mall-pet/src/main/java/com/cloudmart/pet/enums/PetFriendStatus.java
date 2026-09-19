package com.cloudmart.pet.enums;

/**
 * 宠物好友状态：PENDING（申请中）→ ACTIVE（好友）/ REJECTED（已拒绝）。
 * 确认时双方各落一行 ACTIVE，形成双向好友关系。
 */
public enum PetFriendStatus {
    /** 待确认 */
    PENDING,
    /** 好友 */
    ACTIVE,
    /** 已拒绝 */
    REJECTED
}
