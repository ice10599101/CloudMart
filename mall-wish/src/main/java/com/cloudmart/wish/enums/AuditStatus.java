package com.cloudmart.wish.enums;

/**
 * 内容审核状态枚举。
 *
 * <p>与 {@code is_visible} 字段解耦：审核状态反映审核流程，
 * 可见性反映是否对用户展示，便于实现"先发后审"策略。</p>
 */
public enum AuditStatus {
    /** 待审核 */
    PENDING,
    /** 已通过 */
    APPROVED,
    /** 已驳回 */
    REJECTED,
    /** 自动隐藏（命中高危词/风控规则） */
    AUTO_HIDDEN
}
