package com.cloudmart.wish.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Locale;

/**
 * 互动/评论内容净化组件。
 *
 * <p>职责（文档 Sprint 1.2 安全测试要求）：</p>
 * <ul>
 *   <li>XSS 转义：{@code HtmlUtils.htmlEscape} 转义 {@code < > & " '}，入库前执行</li>
 *   <li>敏感词检测：先发后审——命中仅标记（sensitive_hit=true）不阻断，
 *       管理后台按标记筛选后人工下架（文档 4.4 审核策略）</li>
 * </ul>
 *
 * <p>敏感词表通过 {@code wish.sensitive-words} 配置，支持热更新（Nacos refresh）。</p>
 */
@Component
public class WishContentSanitizer {

    private final List<String> sensitiveWords;

    public WishContentSanitizer(@Value("${wish.sensitive-words:}") List<String> sensitiveWords) {
        this.sensitiveWords = sensitiveWords == null ? List.of() :
                sensitiveWords.stream().filter(w -> w != null && !w.isBlank()).toList();
    }

    /**
     * XSS 转义（入库前调用，展示端无需再次转义）。
     */
    /** B12 允许保留的富文本标签（其余标签剥壳保留内文） */
    private static final java.util.regex.Pattern ALLOWED_TAG =
            java.util.regex.Pattern.compile("(?i)^(/?(p|br|b|strong|i|em|u|ul|ol|li|h[1-6]|blockquote|span))$");
    /** 危险标签名单（整块移除，含内容） */
    private static final List<String> DANGEROUS_TAGS =
            List.of("script", "style", "iframe", "object", "embed", "svg", "math");
    private static final java.util.regex.Pattern TAG_PATTERN =
            java.util.regex.Pattern.compile("(?s)<[^>]*>");
    /** 危险 URL scheme（javascript:/vbscript:/data:） */
    private static final java.util.regex.Pattern DANGEROUS_URL =
            java.util.regex.Pattern.compile("(?i)\b(javascript|vbscript|data):");
    /** 事件属性 on* */
    private static final java.util.regex.Pattern EVENT_ATTR =
            java.util.regex.Pattern.compile("(?i)\son[a-z]+\s*=\s*(\"[^\"]*\"|'[^']*'|[^\s>]+)");

    /**
     * B12 服务端富文本白名单净化：整块移除 script/style/iframe 等；剥除白名单外标签
     * （保留内文）；移除 on* 事件属性与 javascript:/vbscript:/data: URL。
     * 与前端 DOMPurify 互为纵深，不能以"前端已消毒"替代服务端。
     */
    public String sanitizeRichText(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String cleaned = raw;
        for (String tag : DANGEROUS_TAGS) {
            cleaned = cleaned.replaceAll("(?is)<" + tag + "[^>]*>.*?</" + tag + ">", "");
            cleaned = cleaned.replaceAll("(?is)<" + tag + "[^>]*/?>", "");
        }
        cleaned = EVENT_ATTR.matcher(cleaned).replaceAll("");
        cleaned = TAG_PATTERN.matcher(cleaned).replaceAll(match -> {
            String tag = match.group();
            int nameEnd = tag.length() > 1 && tag.charAt(1) == '/' ? 2 : 1;
            int sp = tag.indexOf(' ');
            String name = (sp > nameEnd ? tag.substring(nameEnd, sp) : tag.substring(nameEnd, tag.length() - 1))
                    .replaceAll("/", "");
            return ALLOWED_TAG.matcher(name).matches() ? tag : "";
        });
        // URL scheme 残留检查（含 img src/a href）
        cleaned = DANGEROUS_URL.matcher(cleaned).replaceAll("blocked:");
        return cleaned;
    }

    /**
     * B12 媒体附件白名单：仅 https/http/oss 内部存储域；单元素长度受限；
     * 引用任意外链（ftp:/data:/javascript: 等）拒绝。
     */
    public boolean isAllowedMediaUrl(String url) {
        if (url == null || url.isBlank() || url.length() > 500) {
            return false;
        }
        return url.startsWith("https://") || url.startsWith("http://") || url.startsWith("oss://");
    }

    public String escapeHtml(String raw) {
        if (raw == null) {
            return null;
        }
        return HtmlUtils.htmlEscape(raw);
    }

    /**
     * 敏感词命中检测（大小写不敏感）。
     *
     * @return true=命中至少一个敏感词
     */
    public boolean containsSensitiveWord(String content) {
        if (content == null || content.isBlank() || sensitiveWords.isEmpty()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return sensitiveWords.stream().anyMatch(lower::contains);
    }

    /**
     * 校验内容不含路径穿越片段（{@code ../}、{@code ..\}，文档安全测试要求）。
     *
     * @return true=内容安全
     */
    public boolean isFreeOfPathTraversal(String content) {
        if (content == null) {
            return true;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return !lower.contains("../") && !lower.contains("..\\");
    }
}
