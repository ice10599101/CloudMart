package com.cloudmart.wish.enums;

/**
 * 预期管理通知选项埋点（文档 2.5 数据回收：转化率分析）。
 */
public enum ExpectedActionType {
    /** 延长预期（跳转心愿编辑页） */
    EXTEND,
    /** 调整目标（跳转 AI 助手页重新拆解） */
    ADJUST,
    /** 转入时间胶囊（跳转胶囊创建页预填） */
    TO_CAPSULE
}
