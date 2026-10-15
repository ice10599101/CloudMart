package com.cloudmart.software.enums;

/**
 * 评论状态（方案 4.2/6.8）：删除保留楼层占位；隐藏不暴露原内容；
 * 命中人工审核仅本人可见"待审核"，审核通过才计入公开评论数并发通知。
 */
public enum SoftwareCommentStatus {
    PENDING,
    VISIBLE,
    HIDDEN,
    DELETED
}
