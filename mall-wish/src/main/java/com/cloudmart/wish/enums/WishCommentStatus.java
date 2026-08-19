package com.cloudmart.wish.enums;

/**
 * 心愿评论状态枚举。
 *
 * <p>先发后审策略（与心愿审核策略一致，见文档第四章 4.4 节）：
 * 评论立即可见（VISIBLE），敏感词命中仅标记（sensitive_hit=1）不阻断，
 * 审核驳回后置 HIDDEN，四端立即不展示。</p>
 */
public enum WishCommentStatus {
    /** 可见 */
    VISIBLE,
    /** 已下架（敏感词自动下架或管理员手动下架） */
    HIDDEN
}
