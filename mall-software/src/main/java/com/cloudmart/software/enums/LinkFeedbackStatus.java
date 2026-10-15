package com.cloudmart.software.enums;

/**
 * 链接失效反馈状态（方案 4.2/6.9）：用户反馈不是最终检测结果；
 * 仅管理员能判 INVALID 或 DISABLED。
 */
public enum LinkFeedbackStatus {
    OPEN,
    VERIFYING,
    RESOLVED,
    REJECTED
}
