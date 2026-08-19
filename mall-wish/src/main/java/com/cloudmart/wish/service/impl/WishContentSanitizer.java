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
