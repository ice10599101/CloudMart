package com.cloudmart.wish.enums;

/**
 * 审核策略枚举。
 *
 * <p>TREE_HOLE 类型心愿使用 STRICT（实时审核高危词），
 * PRIVATE 和 PUBLIC 类型使用 LAZY（先发后审）。</p>
 */
public enum AuditStrategy {
    /** 先发后审（PRIVATE/PUBLIC 默认） */
    LAZY,
    /** 实时审核高危词（TREE_HOLE 默认） */
    STRICT
}
