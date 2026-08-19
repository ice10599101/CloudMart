package com.cloudmart.wish.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiPrivacySanitizer 单元测试（文档 30.4：外发前 PII 脱敏）。
 */
@DisplayName("AiPrivacySanitizer 单元测试")
class AiPrivacySanitizerTest {

    private final AiPrivacySanitizer sanitizer = new AiPrivacySanitizer();

    @Test
    @DisplayName("手机号脱敏")
    void shouldMaskPhoneNumber() {
        String result = sanitizer.sanitize("有事打我电话13812345678谢谢");
        assertThat(result).isEqualTo("有事打我电话[已隐藏]谢谢");
    }

    @Test
    @DisplayName("邮箱脱敏")
    void shouldMaskEmail() {
        String result = sanitizer.sanitize("发邮件到 someone@example.com 吧");
        assertThat(result).isEqualTo("发邮件到 [已隐藏] 吧");
    }

    @Test
    @DisplayName("身份证号脱敏")
    void shouldMaskIdCard() {
        String result = sanitizer.sanitize("证号110101199003077758记一下");
        assertThat(result).isEqualTo("证号[已隐藏]记一下");
    }

    @Test
    @DisplayName("混合 PII 全部脱敏，正文不受影响")
    void shouldMaskAllPiiTypes() {
        String result = sanitizer.sanitize(
                "电话13812345678，邮箱a.b+c@test.cn，证号11010119900307775X，心情很低落");
        assertThat(result)
                .doesNotContain("13812345678")
                .doesNotContain("a.b+c@test.cn")
                .doesNotContain("11010119900307775X")
                .contains("[已隐藏]")
                .contains("心情很低落");
    }

    @Test
    @DisplayName("长数字串中的 11 位片段不误伤（前后紧邻数字非手机号）")
    void shouldNotMaskDigitsInsideLongerNumber() {
        String result = sanitizer.sanitize("订单号9138123456789012请核对");
        assertThat(result).isEqualTo("订单号9138123456789012请核对");
    }

    @Test
    @DisplayName("null 与空字符串原样返回")
    void shouldPassThroughNullAndEmpty() {
        assertThat(sanitizer.sanitize(null)).isNull();
        assertThat(sanitizer.sanitize("")).isEmpty();
    }
}
