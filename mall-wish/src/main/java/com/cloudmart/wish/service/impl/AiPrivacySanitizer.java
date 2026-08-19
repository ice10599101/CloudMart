package com.cloudmart.wish.service.impl;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * AI 外发内容隐私脱敏组件（文档 30.4 数据安全）。
 *
 * <p>发送给 DashScope 前移除用户手机号 / 邮箱 / 身份证号（正则替换为占位符），
 * 防止个人信息传输至第三方。纯函数组件，无外部依赖。</p>
 */
@Component
public class AiPrivacySanitizer {

    /** 大陆手机号：1[3-9] 开头 11 位 */
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 邮箱地址 */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** 身份证号：18 位（末位可为 X/x） */
    private static final Pattern ID_CARD_PATTERN =
            Pattern.compile("(?<!\\d)\\d{17}[0-9Xx](?!\\d)");

    private static final String MASK_PLACEHOLDER = "[已隐藏]";

    /**
     * 脱敏用户消息：依次移除身份证号、手机号、邮箱。
     */
    public String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String sanitized = ID_CARD_PATTERN.matcher(raw).replaceAll(MASK_PLACEHOLDER);
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(MASK_PLACEHOLDER);
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(MASK_PLACEHOLDER);
        return sanitized;
    }
}
