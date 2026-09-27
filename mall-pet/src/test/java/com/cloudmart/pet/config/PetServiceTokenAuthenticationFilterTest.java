package com.cloudmart.pet.config;

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
 * SEC-01 服务令牌过滤器测试：合法签发方+作用域建立 INTERNAL 身份；
 * 错 scope/错签发方/过期/无令牌/非保护路径一律不建立身份（T01/T03 服务端断言）。
 */
@DisplayName("PetServiceTokenAuthenticationFilter 服务令牌认证")
class PetServiceTokenAuthenticationFilterTest {

    private static final String SECRET = "unit-test-pet-service-token-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");

    private PetServiceTokenAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PetServiceTokenAuthenticationFilter(
                new PetSecurityProperties(SECRET, "http://127.0.0.1:9001/oauth2/jwks", 30),
                Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private String token(String issuer, String scope) {
        return ServiceTokenCodec.sign(issuer, "mall-pet", scope, Duration.ofSeconds(60), SECRET, NOW);
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
    @DisplayName("mall-admin 令牌访问 /admin：建立 INTERNAL 身份，principal=签发方")
    void adminTokenOnAdminPath_authenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pet/reports");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-admin", "pet:admin"));
        runFilter(request);

        Authentication auth = currentAuth();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("mall-admin");
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals(PetServiceTokenAuthenticationFilter.ROLE_INTERNAL));
    }

    @Test
    @DisplayName("错 scope（wish:admin）访问 /admin：拒绝")
    void wrongScope_rejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pet/reports");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-admin", "wish:admin"));
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("错签发方（mall-job）访问 /admin：拒绝")
    void wrongIssuer_rejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pet/operations");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-job", "pet:admin"));
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("过期令牌拒绝")
    void expiredToken_rejected() throws Exception {
        String expired = ServiceTokenCodec.sign("mall-admin", "mall-pet", "pet:admin",
                Duration.ofSeconds(60), SECRET, NOW.minusSeconds(600));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pet/reports");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, expired);
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("无令牌不建立身份（后续由 Spring Security 401）")
    void missingToken_anonymous() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pet/reports");
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("保护路径之外携带令牌不授予 INTERNAL（令牌仅对映射路径生效）")
    void tokenOutsideProtectedPath_noInternalRole() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-admin", "pet:admin"));
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("矩阵参数不影响保护前缀匹配")
    void resolveRequirement_matrixParams() {
        assertThat(PetServiceTokenAuthenticationFilter.resolveRequirement("/admin;jsessionid=x/pet/reports"))
                .isNotNull();
        assertThat(PetServiceTokenAuthenticationFilter.resolveRequirement("/administrators"))
                .isNull();
        assertThat(PetServiceTokenAuthenticationFilter.resolveRequirement("/admin/pet/reports"))
                .isNotNull();
        assertThat(PetServiceTokenAuthenticationFilter.resolveRequirement(null))
                .isNull();
    }

    @Test
    @DisplayName("密钥缺失 fail-closed：携带合法形态令牌也拒绝")
    void missingSecret_failClosed() throws Exception {
        PetServiceTokenAuthenticationFilter failClosedFilter = new PetServiceTokenAuthenticationFilter(
                new PetSecurityProperties("", "http://127.0.0.1:9001/oauth2/jwks", 30),
                Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/pet/reports");
        request.addHeader(ServiceTokenCodec.HEADER_NAME, token("mall-admin", "pet:admin"));
        failClosedFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(currentAuth()).isNull();
    }
}
