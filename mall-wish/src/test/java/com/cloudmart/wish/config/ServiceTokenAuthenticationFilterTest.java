package com.cloudmart.wish.config;

import com.cloudmart.common.security.ServiceTokenCodec;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B01 服务令牌过滤器测试：合法签发方+作用域建立 INTERNAL 身份；
 * 错 scope/错签发方/过期/无令牌/非保护路径一律不建立身份（T01/T02 服务端断言）。
 */
@DisplayName("ServiceTokenAuthenticationFilter 服务令牌认证")
class ServiceTokenAuthenticationFilterTest {

    private static final String SECRET = "unit-test-wish-service-token-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private ServiceTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ServiceTokenAuthenticationFilter(
                new WishSecurityProperties(SECRET, "http://127.0.0.1:9001/oauth2/jwks", 30),
                Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private String token(String issuer, String scope) {
        return ServiceTokenCodec.sign(issuer, "mall-wish", scope, Duration.ofSeconds(60), SECRET, NOW);
    }

    private void runFilter(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("mall-job 令牌访问 /internal/jobs：建立 INTERNAL 身份")
    void jobTokenOnJobsPath_authenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/jobs/overdue-scan");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-job", "wish:jobs"));
        runFilter(request);

        Authentication auth = currentAuth();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("mall-job");
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals(ServiceTokenAuthenticationFilter.ROLE_INTERNAL));
    }

    @Test
    @DisplayName("mall-pet 令牌访问 /internal/jobs：scope 不匹配，拒绝")
    void petTokenOnJobsPath_rejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/jobs/overdue-scan");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-pet", "wish:pet"));
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("过期令牌拒绝")
    void expiredToken_rejected() throws Exception {
        String expired = ServiceTokenCodec.sign("mall-admin", "mall-wish", "wish:admin",
                Duration.ofSeconds(60), SECRET, NOW.minusSeconds(600));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/wishes");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, expired);
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("无令牌不建立身份（后续由 Spring Security 401）")
    void missingToken_anonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/internal/pet-support/starlight/earn");
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("保护路径之外携带令牌不授予 INTERNAL（令牌仅对映射路径生效）")
    void tokenOutsideProtectedPath_noInternalRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wishes/1");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-pet", "wish:pet"));
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("路径要求解析：段边界、矩阵参数、保护前缀")
    void resolveRequirement_pathSemantics() {
        assertThat(ServiceTokenAuthenticationFilter.resolveRequirement("/admin/wishes"))
                .isNotNull();
        assertThat(ServiceTokenAuthenticationFilter.resolveRequirement("/administer"))
                .isNull();
        assertThat(ServiceTokenAuthenticationFilter.resolveRequirement("/internal/jobs;x=1/overdue-scan"))
                .isNotNull();
        assertThat(ServiceTokenAuthenticationFilter.resolveRequirement("/internal/tree-env/scan"))
                .isNotNull();
        assertThat(ServiceTokenAuthenticationFilter.resolveRequirement("/internal/pet-support/starlight/earn"))
                .isNotNull();
        assertThat(ServiceTokenAuthenticationFilter.resolveRequirement("/wishes"))
                .isNull();
    }
}
