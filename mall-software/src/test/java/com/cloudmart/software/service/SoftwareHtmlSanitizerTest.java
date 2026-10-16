package com.cloudmart.software.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 富文本纯文本提取单测（搜索/审核输入）。
 * 服务端 HTML 消毒已于 2026-10-17 按用户决定移除（原样保存，展示端 DOMPurify 兜底）。
 */
class SoftwareHtmlSanitizerTest {

    private final SoftwareHtmlSanitizer sanitizer = new SoftwareHtmlSanitizer();

    @Test
    @DisplayName("extractText：标签剥离后的纯文本供审核（块级元素间空格归一化）")
    void extractText_plainText() {
        String text = sanitizer.extractText("<h2>功能</h2><p>支持<strong>批量</strong>导出</p>");
        // Jsoup text() 在块级元素间产生空格，语义等价
        assertThat(text.replaceAll("\\s+", "")).isEqualTo("功能支持批量导出");
        assertThat(sanitizer.extractText("")).isEmpty();
    }

    @Test
    @DisplayName("extractText：null 与空白输入返回空串")
    void extractText_nullAndBlank() {
        assertThat(sanitizer.extractText(null)).isEmpty();
        assertThat(sanitizer.extractText("   ")).isEmpty();
    }
}
