package com.cloudmart.wish.enums;

/**
 * 用户同意类型（文档 1.2 节 ⑳ / 34.2 合规留痕）。
 */
public enum ConsentType {
    /** 隐私政策 */
    PRIVACY_POLICY,
    /** AI 数据处理（使用 AI 功能前必须同意，文档 39.8） */
    AI_DATA_PROCESSING,
    /** 品牌数据共享（Phase 3 引入） */
    BRAND_DATA_SHARE
}
