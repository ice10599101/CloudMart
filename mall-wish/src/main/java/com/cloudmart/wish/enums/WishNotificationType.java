package com.cloudmart.wish.enums;

/**
 * 心愿宇宙通知类型（文档 1.2 节 ⑰，13 类）。
 *
 * <p>与 mall-notification 推送通道的消息类型枚举保持一致。</p>
 */
public enum WishNotificationType {
    WISH_COMMENT,
    WISH_LIGHT,
    WISH_FULFILL,
    CAPSULE_OPEN,
    /** AI 陪伴提醒（Sprint 2.5） */
    AI_REMINDER,
    /** 预期管理/打卡提醒（Sprint 2.5） */
    CHECKIN_REMINDER,
    MATCH_RECOMMEND,
    BRAND_REWARD,
    ENCOUNTER_LETTER,
    DEVICE_OFFLINE,
    LEVEL_UP,
    BADGE_EARNED,
    SYSTEM
}
