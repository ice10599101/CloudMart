package com.cloudmart.software.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 4.1/11.1 富文本消毒单测（A13）：Tiptap 兼容子集白名单；脚本/事件/图片剥离；
 * 链接仅 https；提取纯文本供审核。
 */
class SoftwareHtmlSanitizerTest {

    private final SoftwareHtmlSanitizer sanitizer = new SoftwareHtmlSanitizer();

    @Test
    @DisplayName("保留 Tiptap 结构标签与 https 链接")
    void sanitize_keepsAllowedSubset() {
        String html = "<h2>功能</h2><p>支持 <strong>批量</strong> 导出与 <em>深色</em> 模式，"
                + "详见 <a href=\"https://example.com/docs\">文档</a>。</p>"
                + "<ul><li>项目一</li><li>项目二</li></ul>";
        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
                .contains("<h2>").contains("<strong>").contains("<em>")
                .contains("<a href=\"https://example.com/docs\"")
                .contains("<li>").doesNotContain("javascript");
    }

    @Test
    @DisplayName("A13：script/事件属性/img/style 全部剥离，预览与详情不执行脚本")
    void sanitize_stripsDangerousContent() {
        String evil = "<p onclick=\"alert(1)\">正常</p>"
                + "<script>alert('xss')</script>"
                + "<img src=\"data:image/png;base64,iVBOR\">"
                + "<img src=\"javascript:alert(3)\">"
                + "<a href=\"javascript:alert(2)\">坏链接</a>"
                + "<iframe src=\"https://evil.example.com\"></iframe>";
        String sanitized = sanitizer.sanitize(evil);

        assertThat(sanitized)
                .doesNotContain("script").doesNotContain("alert")
                .doesNotContain("data:image").doesNotContain("iframe")
                .doesNotContain("javascript:").doesNotContain("onclick")
                .contains("正常");
    }

    @Test
    @DisplayName("正文图片：/files/ 与 https 外链保留，data:/javascript:/http 剥离")
    void sanitize_keepsLocalAndHttpsImagesOnly() {
        String html = "<p>正文</p>"
                + "<img src=\"/files/19\">"
                + "<img src=\"https://cdn.example.com/pic.png\">"
                + "<img src=\"data:image/png;base64,iVBOR\">"
                + "<img src=\"http://insecure.example.com/pic.png\">"
                + "<img src=\"javascript:alert(1)\">";
        String sanitized = sanitizer.sanitize(html);

        assertThat(sanitized)
                .contains("src=\"/files/19\"")
                .contains("https://cdn.example.com/pic.png")
                .doesNotContain("data:image")
                .doesNotContain("http://insecure")
                .doesNotContain("javascript:");
    }

    @Test
    @DisplayName("http 链接被剥离 href（仅 https）；空输入返回空串")
    void sanitize_httpsOnlyLinks() {
        String sanitized = sanitizer.sanitize("<p><a href=\"http://example.com/x\">链接</a></p>");
        assertThat(sanitized).doesNotContain("href=");

        assertThat(sanitizer.sanitize(null)).isEmpty();
        assertThat(sanitizer.sanitize("   ")).isEmpty();
    }

    @Test
    @DisplayName("extractText：标签剥离后的纯文本供审核（块级元素间空格归一化）")
    void extractText_plainText() {
        String text = sanitizer.extractText("<h2>功能</h2><p>支持<strong>批量</strong>导出</p>");
        // Jsoup text() 在块级元素间产生空格，语义等价
        assertThat(text.replaceAll("\\s+", "")).isEqualTo("功能支持批量导出");
        assertThat(sanitizer.extractText("")).isEmpty();
    }
}
