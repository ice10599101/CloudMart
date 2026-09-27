package com.cloudmart.common.security;

import com.cloudmart.common.context.AdminSecurityContext;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * SEC-01 用户/管理员 JWT 入站过滤器：RS256 验签、身份域（scope）判定、
 * X-User-Id 强制改写与管理员上下文填充。
 */
@DisplayName("UserJwtAuthenticationFilter 用户/管理员 JWT 入站")
class UserJwtAuthenticationFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    private CloudmartSecurityProperties properties;
    private UserJwtAuthenticationFilter filter;
    private JWKSource<SecurityContext> jwkSource;
    private RSASSASigner signer;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, com.nimbusds.jose.JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-kid")
                .build();
        jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        signer = new RSASSASigner(rsaKey);

        properties = new CloudmartSecurityProperties();
        properties.setServiceId("mall-order");
        filter = new UserJwtAuthenticationFilter(properties, jwkSource);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AdminSecurityContext.clear();
    }

    private String token(String scope, String subject, String... extraClaims) throws Exception {
        Instant base = Instant.now();
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(base))
                .expirationTime(Date.from(base.plusSeconds(900)));
        if (scope != null) {
            builder.claim("scope", scope);
        }
        for (int i = 0; i + 1 < extraClaims.length; i += 2) {
            builder.claim(extraClaims[i], extraClaims[i + 1]);
        }
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(),
                builder.build());
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/1");
        request.setRequestURI("/orders/1");
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        // 模拟直连伪造场景：外部自填的 X-User-Id 必须被改写为令牌主体
        request.addHeader("X-User-Id", "999");
        return request;
    }

    @Test
    @DisplayName("用户令牌建立 ROLE_USER 并强制改写 X-User-Id 为令牌主体")
    void userToken_roleUser_andForcedUserIdHeader() throws Exception {
        CapturingChain capturingChain = new CapturingChain();
        filter.doFilter(request(token("user", "42")), new MockHttpServletResponse(), capturingChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        assertThat(capturingChain.captured.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(AdminSecurityContext.get()).isNull();
    }

    @Test
    @DisplayName("管理员令牌建立 ROLE_ADMIN 并在请求处理期间填充 AdminSecurityContext")
    void adminToken_roleAdmin_andAdminContext() throws Exception {
        ContextCapturingChain capturingChain = new ContextCapturingChain();
        filter.doFilter(request(token("admin", "7", "perms", "monitor:job:list,monitor:job:run",
                        "username", "ops", "deptId", "3")),
                new MockHttpServletResponse(), capturingChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        AdminSecurityContext context = capturingChain.captured;
        assertThat(context).isNotNull();
        assertThat(context.userId()).isEqualTo(7L);
        assertThat(context.username()).isEqualTo("ops");
        assertThat(context.role()).isEqualTo("admin");
        assertThat(context.deptId()).isEqualTo(3L);
        assertThat(context.hasPermission("monitor:job:list")).isTrue();
        assertThat(context.hasPermission("other:perm")).isFalse();
    }

    @Test
    @DisplayName("未知身份域 scope=service 拒绝建立身份")
    void unknownScope_rejected() throws Exception {
        filter.doFilter(request(token("service", "1")), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("过期令牌拒绝")
    void expiredToken_rejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .claim("scope", "user")
                .issueTime(Date.from(Instant.now().minusSeconds(1800)))
                .expirationTime(Date.from(Instant.now().minusSeconds(900)))
                .build();
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
        signedJWT.sign(signer);

        filter.doFilter(request(signedJWT.serialize()), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("请求结束后管理员上下文被清理，不发生 ThreadLocal 泄漏")
    void adminContext_clearedAfterRequest() throws Exception {
        filter.doFilter(request(token("admin", "7")), new MockHttpServletResponse(), chain);

        assertThat(AdminSecurityContext.get()).isNull();
    }

    @Test
    @DisplayName("无 Authorization 头不建立身份")
    void noAuthorizationHeader_noIdentity() throws ServletException, IOException {
        filter.doFilter(request(null), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    /** 捕获下游实际收到的请求（可能是强制改写头后的包装器），验证行为 */
    private static final class CapturingChain implements jakarta.servlet.FilterChain {
        jakarta.servlet.http.HttpServletRequest captured;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                             jakarta.servlet.ServletResponse servletResponse) {
            captured = (jakarta.servlet.http.HttpServletRequest) servletRequest;
        }
    }

    /** 在请求处理期间捕获 AdminSecurityContext（过滤器在请求结束后清理，防 ThreadLocal 泄漏） */
    private static final class ContextCapturingChain implements jakarta.servlet.FilterChain {
        AdminSecurityContext captured;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest servletRequest,
                             jakarta.servlet.ServletResponse servletResponse) {
            captured = AdminSecurityContext.get();
        }
    }
}
