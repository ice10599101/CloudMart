package com.cloudmart.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B01 服务令牌编解码测试：签名/校验、密钥强度、有效期、audience/issuer/scope 强校验、
 * 篡改检测与常量时间比较路径覆盖。
 */
@DisplayName("ServiceTokenCodec 服务令牌")
class ServiceTokenCodecTest {

    private static final String SECRET = "unit-test-service-token-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final Duration TTL = Duration.ofSeconds(120);
    private static final Duration SKEW = Duration.ofSeconds(30);

    @Test
    @DisplayName("签发后可校验通过，声明完整回读")
    void signAndVerify_roundTrip() throws Exception {
        String token = ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs", TTL, SECRET, NOW);

        ServiceTokenCodec.ServiceTokenClaims claims = ServiceTokenCodec.verify(
                token, SECRET, "mall-wish", "mall-job", "wish:jobs", NOW.plusSeconds(60), SKEW);

        assertThat(claims.issuer()).isEqualTo("mall-job");
        assertThat(claims.audience()).isEqualTo("mall-wish");
        assertThat(claims.scope()).isEqualTo("wish:jobs");
        assertThat(claims.exp()).isEqualTo(NOW.plus(TTL).getEpochSecond());
    }

    @Test
    @DisplayName("过期令牌拒绝（含 skew 容忍边界）")
    void verify_expiredToken_rejected() {
        String token = ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs", TTL, SECRET, NOW);

        // exp = NOW+120；NOW+120+skew(30) 之后必然过期
        assertThatThrownBy(() -> ServiceTokenCodec.verify(token, SECRET, "mall-wish", "mall-job",
                "wish:jobs", NOW.plus(TTL).plusSeconds(31), SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.EXPIRED));
    }

    @Test
    @DisplayName("skew 容忍窗口内未过期的令牌可通过")
    void verify_withinClockSkew_accepted() throws Exception {
        String token = ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs", TTL, SECRET, NOW);

        ServiceTokenCodec.ServiceTokenClaims claims = ServiceTokenCodec.verify(
                token, SECRET, "mall-wish", "mall-job", "wish:jobs", NOW.plus(TTL).plusSeconds(29), SKEW);
        assertThat(claims.issuer()).isEqualTo("mall-job");
    }

    @Test
    @DisplayName("篡改 payload 签名校验失败")
    void verify_tamperedPayload_rejected() throws Exception {
        String token = ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs", TTL, SECRET, NOW);
        String[] parts = token.split("\\.");
        String tamperedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"iss\":\"mall-admin\",\"aud\":\"mall-wish\",\"scope\":\"wish:admin\","
                        + "\"iat\":0,\"exp\":9999999999,\"jti\":\"x\"}").getBytes());
        String forged = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> ServiceTokenCodec.verify(forged, SECRET, "mall-wish", null, null, NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.BAD_SIGNATURE));
    }

    @Test
    @DisplayName("audience/issuer/scope 不匹配分别报对应错误")
    void verify_wrongClaims_rejected() {
        String token = ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs", TTL, SECRET, NOW);

        assertThatThrownBy(() -> ServiceTokenCodec.verify(token, SECRET, "mall-user", "mall-job", "wish:jobs", NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.WRONG_AUDIENCE));

        assertThatThrownBy(() -> ServiceTokenCodec.verify(token, SECRET, "mall-wish", "mall-admin", "wish:jobs", NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.WRONG_ISSUER));

        assertThatThrownBy(() -> ServiceTokenCodec.verify(token, SECRET, "mall-wish", "mall-job", "wish:pet", NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.WRONG_SCOPE));
    }

    @Test
    @DisplayName("错误密钥签出的令牌拒绝")
    void verify_wrongSecret_rejected() {
        String token = ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs", TTL, SECRET, NOW);

        assertThatThrownBy(() -> ServiceTokenCodec.verify(token, SECRET + "-rotated", "mall-wish",
                "mall-job", "wish:jobs", NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.BAD_SIGNATURE));
    }

    @Test
    @DisplayName("弱密钥与空令牌在入口即拒绝")
    void weakSecretAndBlankToken_rejected() {
        assertThatThrownBy(() -> ServiceTokenCodec.sign("mall-job", "mall-wish", "wish:jobs",
                TTL, "short", NOW))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> ServiceTokenCodec.verify(null, SECRET, "mall-wish", null, null, NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.MALFORMED));

        assertThatThrownBy(() -> ServiceTokenCodec.verify("a.b", SECRET, "mall-wish", null, null, NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.MALFORMED));
    }

    @Test
    @DisplayName("iat 在未来超出 skew 拒绝")
    void verify_futureIat_rejected() throws Exception {
        // 手工构造 iat=NOW+1h 的合法签名令牌（sign() 不允许自定义 iat）
        String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"mall-job\",\"aud\":\"mall-wish\",\"scope\":\"wish:jobs\",\"iat\":"
                        + NOW.plusSeconds(3600).getEpochSecond() + ",\"exp\":"
                        + NOW.plusSeconds(7200).getEpochSecond() + ",\"jti\":\"t\"}").getBytes());
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes());
        String signingInput = header + "." + payload;

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(), "HmacSHA256"));
        String signature = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes()));
        String forged = signingInput + "." + signature;

        assertThatThrownBy(() -> ServiceTokenCodec.verify(forged, SECRET, "mall-wish", "mall-job", "wish:jobs", NOW, SKEW))
                .isInstanceOfSatisfying(ServiceTokenCodec.ServiceTokenException.class,
                        e -> assertThat(e.getError()).isEqualTo(ServiceTokenCodec.ServiceTokenError.MALFORMED));
    }
}
