package com.cloudmart.wish.enums;

/**
 * AI 拆解目标状态机（文档 1.2 节 ㊱b）。
 *
 * <p>流转：PENDING → IN_PROGRESS → COMPLETED；任意非终态 → CANCELLED。</p>
 */
public enum GoalStatus {
    /** 待开始（用户勾选 AI 拆解步骤后创建） */
    PENDING,
    /** 进行中 */
    IN_PROGRESS,
    /** 已完成（终态） */
    COMPLETED,
    /** 已取消（终态） */
    CANCELLED
}
