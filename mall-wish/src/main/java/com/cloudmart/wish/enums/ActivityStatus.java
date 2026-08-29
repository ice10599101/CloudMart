package com.cloudmart.wish.enums;

/**
 * 活动状态机（Sprint 3.5）：筹备 DRAFT → 进行中 ACTIVE → 结束 ENDED →
 * 归档 ARCHIVED（归档后入口消失，详情页仍可访问）。
 */
public enum ActivityStatus {
    DRAFT,
    ACTIVE,
    ENDED,
    ARCHIVED
}
