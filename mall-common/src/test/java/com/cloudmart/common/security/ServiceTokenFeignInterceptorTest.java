package com.cloudmart.common.security;

import feign.RequestTemplate;
import feign.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SEC-01 出站 Feign 服务令牌签名拦截器：按目标能力域签名、未配置目标不签名、
 * 密钥缺失 fail loud。
 */
@DisplayName("ServiceTokenFeignInterceptor 出站签名")
class ServiceTokenFeignInterceptorTest {

    private static final String SECRET = "unit-test-service-token-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private CloudmartSecurityProperties properties;
    private ServiceTokenFeignInterceptor interceptor;

    @BeforeEach
    void setUp() {
        properties = new CloudmartSecurityProperties();
        properties.setServiceId("mall-order");
        properties.setServiceTokenSecret(SECRET);
        properties.setOutboundScopes(Map.of(
                "mall-inventory", "inventory:trade",
                "mall-coupon", "coupon:trade"));
        interceptor = new ServiceTokenFeignInterceptor(properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private RequestTemplate templateFor(String targetService) {
        RequestTemplate template = new RequestTemplate();
        template.feignTarget(Target.EmptyTarget.create(Object.class, targetService));
        template.uri("/test");
        return template;
    }

    @Test
    @DisplayName("已配置能力域的目标被签发令牌，iss/aud/scope 正确")
    void configuredTarget_signed() throws Exception {
        RequestTemplate template = templateFor("mall-inventory");
        interceptor.apply(template);

        String token = template.headers().get(ServiceTokenCodec.HEADER_NAME).iterator().next();
        ServiceTokenCodec.ServiceTokenClaims claims = ServiceTokenCodec.verify(
                token, SECRET, "mall-inventory", "mall-order", "inventory:trade",
                NOW.plusSeconds(30), java.time.Duration.ofSeconds(30));
        assertThat(claims.issuer()).isEqualTo("mall-order");
    }

    @Test
    @DisplayName("未配置能力域的目标不签名（公开端点无需令牌）")
    void unconfiguredTarget_notSigned() {
        RequestTemplate template = templateFor("mall-product");
        interceptor.apply(template);

        assertThat(template.headers().get(ServiceTokenCodec.HEADER_NAME)).isNull();
    }

    @Test
    @DisplayName("密钥缺失时抛出明确异常，绝不静默降级")
    void missingSecret_failLoud() {
        properties.setServiceTokenSecret("");
        assertThatThrownBy(() -> interceptor.apply(templateFor("mall-inventory")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mall-inventory");
    }
}
