package com.cloudmart.wish.constant;

/**
 * 心愿宇宙模块错误码常量。
 *
 * <p>所有错误码以 WISH_ 前缀，对应文档 2.1 节 errors 定义。
 * HTTP 状态映射在 {@code GlobalExceptionHandler} 中配置。</p>
 */
public final class WishErrorCodes {

    private WishErrorCodes() {}

    // --- 400 Bad Request ---
    public static final String WISH_TITLE_REQUIRED = "WISH_TITLE_REQUIRED";
    public static final String WISH_DESCRIPTION_REQUIRED = "WISH_DESCRIPTION_REQUIRED";
    public static final String WISH_CATEGORY_INVALID = "WISH_CATEGORY_INVALID";
    public static final String WISH_VISIBILITY_INVALID = "WISH_VISIBILITY_INVALID";
    public static final String WISH_VALIDATION_ERROR = "WISH_VALIDATION_ERROR";
    public static final String WISH_INTERACTION_TYPE_INVALID = "WISH_INTERACTION_TYPE_INVALID";
    public static final String WISH_INTERACTION_NOT_FOUND = "WISH_INTERACTION_NOT_FOUND";

    // --- 403 Forbidden ---
    public static final String WISH_NOT_AUTHOR = "WISH_NOT_AUTHOR";
    public static final String WISH_RESTRICTED = "WISH_RESTRICTED";
    public static final String WISH_FORBIDDEN = "WISH_FORBIDDEN";
    /** 未同意 AI 数据处理协议（文档 2.11 / 39.8） */
    public static final String WISH_CONSENT_REQUIRED = "WISH_CONSENT_REQUIRED";

    // --- 404 Not Found ---
    public static final String WISH_NOT_FOUND = "WISH_NOT_FOUND";
    public static final String WISH_CATEGORY_NOT_FOUND = "WISH_CATEGORY_NOT_FOUND";
    /** 还愿记录不存在（心愿未还愿或已撤回还愿故事） */
    public static final String WISH_FULFILLMENT_NOT_FOUND = "WISH_FULFILLMENT_NOT_FOUND";

    // --- 409 Conflict ---
    public static final String WISH_STATUS_CONFLICT = "WISH_STATUS_CONFLICT";
    public static final String WISH_ALREADY_INTERACTED = "WISH_ALREADY_INTERACTED";
    public static final String WISH_ALREADY_CHECKIN_TODAY = "WISH_ALREADY_CHECKIN_TODAY";
    public static final String WISH_VERSION_CONFLICT = "WISH_VERSION_CONFLICT";
    public static final String WISH_NOT_FULFILLABLE = "WISH_NOT_FULFILLABLE";

    // --- 402 Payment Required ---
    public static final String WISH_STARLIGHT_INSUFFICIENT = "WISH_STARLIGHT_INSUFFICIENT";

    // --- 429 Too Many Requests ---
    public static final String WISH_RATE_LIMITED = "WISH_RATE_LIMITED";
    /** AI 功能调用频次达上限（文档 30.3：树洞 10 次/日） */
    public static final String WISH_AI_RATE_LIMITED = "WISH_AI_RATE_LIMITED";

    // 徽章管理（Sprint 1.8）
    public static final String BADGE_NOT_FOUND = "BADGE_NOT_FOUND";
    public static final String BADGE_CODE_DUPLICATED = "BADGE_CODE_DUPLICATED";
    public static final String BADGE_CONDITION_INVALID = "BADGE_CONDITION_INVALID";

    // --- 503 Service Unavailable ---
    /** AI 服务不可用（重试后仍失败，文档 30.1/30.3） */
    public static final String WISH_AI_UNAVAILABLE = "WISH_AI_UNAVAILABLE";
}
