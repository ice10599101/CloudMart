package com.cloudmart.wish.enums;

/**
 * 活动参与状态：普通参与 JOINED；合伙人申请 PENDING → APPROVED/REJECTED。
 */
public enum ActivityParticipantStatus {
    JOINED,
    PENDING,
    APPROVED,
    REJECTED
}
