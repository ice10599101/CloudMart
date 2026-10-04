package com.cloudmart.common.aspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T24：审计敏感数据脱敏——密码/令牌/地址不入审计表（两级防御：参数名名单 +
 * 序列化串级正则兜底 record/Map toString 展开）。
 */
@DisplayName("OperLogSanitizer 审计脱敏（T24）")
class OperLogSanitizerTest {

    @Test
    @DisplayName("参数名级：password/token/secret/credential 命中名单")
    void sensitiveParamNames() {
        assertThat(OperLogSanitizer.isSensitiveParamName("password")).isTrue();
        assertThat(OperLogSanitizer.isSensitiveParamName("newPassword")).isTrue();
        assertThat(OperLogSanitizer.isSensitiveParamName("refreshToken")).isTrue();
        assertThat(OperLogSanitizer.isSensitiveParamName("serviceTokenSecret")).isTrue();
        assertThat(OperLogSanitizer.isSensitiveParamName("authorization")).isTrue();
        assertThat(OperLogSanitizer.isSensitiveParamName("username")).isFalse();
        assertThat(OperLogSanitizer.isSensitiveParamName("reason")).isFalse();
        assertThat(OperLogSanitizer.isSensitiveParamName(null)).isFalse();
    }

    @Test
    @DisplayName("串级兜底：record toString 展开的 password= 被打码")
    void serializedPasswordMasked() {
        // record LoginRequest(username, password) 的 toString 展开
        String serialized = "{request=LoginRequest[username=admin, password=SuperSecret123]}";
        String out = OperLogSanitizer.sanitizeSerialized(serialized);

        assertThat(out).doesNotContain("SuperSecret123");
        assertThat(out).contains("password=***");
        assertThat(out).contains("username=admin");
    }

    @Test
    @DisplayName("串级兜底：Map toString 的 token= 与 accessToken= 打码")
    void serializedTokenMasked() {
        String serialized = "{headers={Authorization=Bearer abc.def.gh, accessToken=xyz}}";
        String out = OperLogSanitizer.sanitizeSerialized(serialized);

        assertThat(out).doesNotContain("abc.def.gh").doesNotContain("xyz");
        assertThat(out).contains("Authorization=***").contains("accessToken=***");
    }

    @Test
    @DisplayName("完整地址不入审计（receiverAddress/fullAddress 打码）")
    void serializedAddressMasked() {
        String serialized = "{order=CreateOrderRequest[receiverName=张三, "
                + "receiverAddress=广东省深圳市南山区科技园路 1 号 101 室]}";
        String out = OperLogSanitizer.sanitizeSerialized(serialized);

        assertThat(out).doesNotContain("科技园路");
        assertThat(out).contains("receiverAddress=***");
        assertThat(out).contains("receiverName=张三");
    }

    @Test
    @DisplayName("非敏感串原样返回（审计可读性不受影响）")
    void nonSensitiveUntouched() {
        String serialized = "{reason=用户投诉商品破损, caseId=11, refundAmount=50.00}";
        String out = OperLogSanitizer.sanitizeSerialized(serialized);

        assertThat(out).isEqualTo(serialized);
    }

    @Test
    @DisplayName("null/空串安全")
    void nullSafe() {
        assertThat(OperLogSanitizer.sanitizeSerialized(null)).isNull();
        assertThat(OperLogSanitizer.sanitizeSerialized("")).isEmpty();
    }
}
