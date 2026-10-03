package com.cloudmart.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SEC-01 服务令牌入站过滤器：路径要求解析（最长前缀）、iss 白名单、scope 强校验、
 * fail-closed 行为与用户令牌互斥。
 */
@DisplayName("ServiceTokenAuthenticationFilter 服务令牌入站")
class ServiceTokenAuthenticationFilterTest {

    private static final String SECRET = "unit-test-service-token-secret-0123456789abcdef";
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private CloudmartSecurityProperties properties;
    private ServiceTokenAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        properties = new CloudmartSecurityProperties();
        properties.setServiceId("mall-order");
        properties.setServiceTokenSecret(SECRET);
        properties.setServiceTokenPaths(List.of(
                new CloudmartSecurityProperties.ServiceTokenPath(
                        "/admin", List.of("mall-admin"), "order:admin"),
                new CloudmartSecurityProperties.ServiceTokenPath(
                        "/internal/payment-notify", List.of("mall-payment"), "order:internal"),
                new CloudmartSecurityProperties.ServiceTokenPath(
                        "/internal", List.of("mall-order", "mall-payment"), "order:trade")));
        filter = new ServiceTokenAuthenticationFilter(properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String uri, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (token != null) {
            request.addHeader(ServiceTokenCodec.HEADER_NAME, token);
        }
        return request;
    }

    private String sign(String issuer, String scope) {
        return ServiceTokenCodec.sign(issuer, "mall-order", scope,
                java.time.Duration.ofSeconds(120), SECRET, NOW);
    }

    @Test
    @DisplayName("合法令牌（iss/scope 匹配）建立 ROLE_INTERNAL，principal 为签发方")
    void validToken_establishesInternalRole() throws ServletException, IOException {
        filter.doFilter(request("/admin/orders", sign("mall-admin", "order:admin")),
                new MockHttpServletResponse(), chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_INTERNAL"));
        assertThat(auth.getPrincipal()).isEqualTo("mall-admin");
    }

    @Test
    @DisplayName("iss 不在允许列表时拒绝建立身份")
    void wrongIssuer_rejected() throws ServletException, IOException {
        filter.doFilter(request("/admin/orders", sign("mall-payment", "order:admin")),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("scope 与路径要求不匹配时拒绝")
    void wrongScope_rejected() throws ServletException, IOException {
        filter.doFilter(request("/admin/orders", sign("mall-admin", "order:trade")),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("audience 不是本服务时拒绝")
    void wrongAudience_rejected() throws ServletException, IOException {
        String token = ServiceTokenCodec.sign("mall-admin", "mall-payment", "order:admin",
                java.time.Duration.ofSeconds(120), SECRET, NOW);
        filter.doFilter(request("/admin/orders", token), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("无令牌经过受保护路径不建立身份（由授权规则 401 兜底）")
    void missingToken_noIdentity() throws ServletException, IOException {
        filter.doFilter(request("/admin/orders", null), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("非受保护路径上的令牌不产生任何身份")
    void tokenOnUnprotectedPath_noIdentity() throws ServletException, IOException {
        filter.doFilter(request("/orders/123", sign("mall-admin", "order:admin")),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("最长前缀优先：/internal/payment-notify 命中专属要求而非通用 /internal")
    void longestPrefixWins() throws ServletException, IOException {
        // 通用 /internal 允许 mall-payment，但 payment-notify 专属要求也允许 mall-payment——
        // 用仅满足专属 scope 的令牌验证命中的是更长前缀的要求
        filter.doFilter(request("/internal/payment-notify/9",
                        sign("mall-payment", "order:internal")),
                new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        SecurityContextHolder.clearContext();
        // scope 为通用 /internal 要求的令牌不满足专属前缀要求 → 拒绝
        filter.doFilter(request("/internal/payment-notify/9",
                        sign("mall-payment", "order:trade")),
                new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("密钥缺失时 fail-closed：所有服务令牌拒绝")
    void missingSecret_failClosed() throws ServletException, IOException {
        properties.setServiceTokenSecret("");
        filter.doFilter(request("/admin/orders", sign("mall-admin", "order:admin")),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("分号矩阵参数不影响路径匹配")
    void matrixParameters_stripped() throws ServletException, IOException {
        filter.doFilter(request("/admin/orders;jsessionid=abc", sign("mall-admin", "order:admin")),
                new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("S05 轮换：当前密钥签发的令牌正常通过")
    void rotation_currentSecret_accepted() throws ServletException, IOException {
        String newSecret = "rotation-new-secret-0123456789abcdef0123456789";
        properties.setServiceTokenSecret(newSecret);
        properties.setServiceTokenSecretPrevious(SECRET);
        String token = ServiceTokenCodec.sign("mall-admin", "mall-order", "order:admin",
                java.time.Duration.ofSeconds(120), newSecret, NOW);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/admin/orders", token), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull()
                .extracting(a -> a.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_INTERNAL");
    }

    @Test
    @DisplayName("S05 轮换：过渡窗口内旧密钥令牌仍可验签通过（可消费）")
    void rotation_previousSecret_acceptedDuringWindow() throws ServletException, IOException {
        String newSecret = "rotation-new-secret-0123456789abcdef0123456789";
        properties.setServiceTokenSecret(newSecret);
        properties.setServiceTokenSecretPrevious(SECRET);
        // 令牌由旧代密钥签发（滚动重启完成前的存量调用方）
        String token = ServiceTokenCodec.sign("mall-admin", "mall-order", "order:admin",
                java.time.Duration.ofSeconds(120), SECRET, NOW);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/admin/orders", token), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication())
                .isNotNull()
                .extracting(a -> a.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_INTERNAL");
    }

    @Test
    @DisplayName("S05 轮换：未配置旧代密钥时旧密钥令牌被拒绝（轮换完成后撤密钥即失效）")
    void rotation_previousRevoked_rejected() throws ServletException, IOException {
        String newSecret = "rotation-new-secret-0123456789abcdef0123456789";
        properties.setServiceTokenSecret(newSecret);
        // 不配置 previous：轮换已完成的稳态
        String token = ServiceTokenCodec.sign("mall-admin", "mall-order", "order:admin",
                java.time.Duration.ofSeconds(120), SECRET, NOW);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("/admin/orders", token), response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
