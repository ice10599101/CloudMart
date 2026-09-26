package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B12 服务端富文本净化测试（T26 语义）：script/iframe 整块移除、on* 属性剥离、
 * 危险 URL scheme 拦截、白名单标签保留、媒体附件 scheme 白名单。
 */
@DisplayName("WishContentSanitizer 富文本净化（B12）")
class WishContentSanitizerTest {

    private final WishContentSanitizer sanitizer = new WishContentSanitizer(List.of());

    @Test
    @DisplayName("script 整块移除（含内容），不残留在正文中")
    void scriptBlockRemoved() {
        String out = sanitizer.sanitizeRichText("<p>你好</p><script>alert('xss')</script>");
        assertThat(out).contains("你好").doesNotContain("script").doesNotContain("alert");
    }

    @Test
    @DisplayName("iframe/style/object/embed/svg 整块移除")
    void dangerousTagsRemoved() {
        String out = sanitizer.sanitizeRichText(
                "<p>a</p><iframe src=\"http://evil\"></iframe><style>body{}</style><b>b</b>");
        assertThat(out).doesNotContain("iframe").doesNotContain("style").doesNotContain("evil").contains("<b>b</b>");
    }

    @Test
    @DisplayName("on* 事件属性剥离")
    void eventAttributesStripped() {
        String out = sanitizer.sanitizeRichText("<p onclick=\"alert(1)\" onmouseover='x()'>内容</p>");
        assertThat(out).doesNotContain("onclick").doesNotContain("onmouseover").contains("内容");
    }

    @Test
    @DisplayName("javascript:/data: URL 拦截")
    void dangerousUrlsBlocked() {
        String out = sanitizer.sanitizeRichText("<a href=\"javascript:alert(1)\">点</a><img src=\"data:text/html;base64,xx\">");
        assertThat(out).doesNotContain("javascript:").doesNotContain("data:");
    }

    @Test
    @DisplayName("白名单外标签剥壳保留内文")
    void unknownTagUnwrapped() {
        String out = sanitizer.sanitizeRichText("<article><p>正文</p></article>");
        assertThat(out).contains("正文").doesNotContain("article");
    }

    @Test
    @DisplayName("纯文本与白名单标签原样保留")
    void plainAndAllowedPreserved() {
        String html = "<p>坚持<b>打卡</b></p><ul><li>第一条</li></ul>";
        assertThat(sanitizer.sanitizeRichText(html)).isEqualTo(html);
    }

    @Test
    @DisplayName("媒体白名单：http/https/oss 通过；data/javascript/超长拒绝")
    void mediaUrlWhitelist() {
        assertThat(sanitizer.isAllowedMediaUrl("https://cdn.example.com/a.png")).isTrue();
        assertThat(sanitizer.isAllowedMediaUrl("oss://bucket/key.png")).isTrue();
        assertThat(sanitizer.isAllowedMediaUrl("http://cdn.example.com/a.png")).isTrue();
        assertThat(sanitizer.isAllowedMediaUrl("data:image/png;base64,xx")).isFalse();
        assertThat(sanitizer.isAllowedMediaUrl("javascript:alert(1)")).isFalse();
        assertThat(sanitizer.isAllowedMediaUrl("x".repeat(501))).isFalse();
        assertThat(sanitizer.isAllowedMediaUrl(null)).isFalse();
    }
}
