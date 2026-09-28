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
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SEC-01 用户/管理员 JWT 入站过滤器：RS256 验签、完整声明语义（iss/aud/sid/
 * authVersion/身份域一致）、时钟边界（±skew，刚签发立即可用）、撤销检查与
 * fail-closed、X-User-Id 强制改写与管理员上下文填充。
 */
@DisplayName("UserJwtAuthenticationFilter 用户/管理员 JWT 入站")
class UserJwtAuthenticationFilterTest {

    /** 固定测试时钟：验收要求边界时间可控，不依赖真实等待 */
    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");
    private static final long SKEW_SECONDS = 30;

    private CloudmartSecurityProperties properties;
    private UserJwtAuthenticationFilter filter;
    private JWKSource<SecurityContext> jwkSource;
    private RSASSASigner signer;
    private AuthRevocationChecker revocationChecker;

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
        revocationChecker = mock(AuthRevocationChecker.class);
        filter = new UserJwtAuthenticationFilter(properties, jwkSource,
                Clock.fixed(NOW, java.time.ZoneOffset.UTC), revocationChecker);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AdminSecurityContext.clear();
    }

    /** 与 mall-auth JwtProvider 同构的完整声明；可按用例覆盖个别字段 */
    private JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth")
                .audience(List.of("cloudmart-api"))
                .subject("42")
                .claim("scope", "user")
                .claim("subjectType", "user")
                .claim("sid", "session-1")
                .claim("authVersion", 0L)
                .issueTime(Date.from(NOW.minusSeconds(1)))
                .notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900)));
    }

    private String sign(JWTClaimsSet claims) throws com.nimbusds.jose.JOSEException {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
        signedJWT.sign(signer);
        return signedJWT.serialize();
    }

    /** 刚签发的有效用户令牌（nbf=now，登录空窗回归的核心样本） */
    private String userToken() throws com.nimbusds.jose.JOSEException {
        return sign(baseClaims().build());
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

    private void doFilter(String token) throws ServletException, IOException {
        filter.doFilter(request(token), new MockHttpServletResponse(), mock(FilterChain.class));
    }

    @Test
    @DisplayName("刚签发的令牌（nbf=now）立即通过——修复 30 秒登录空窗")
    void justIssuedToken_acceptedImmediately() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(userToken());

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
    }

    @Test
    @DisplayName("nbf 在 skew 容差内（now+29s）接受，超出（now+31s）拒绝")
    void nbfBoundary_respectedBySkew() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(sign(baseClaims()
                .notBeforeTime(Date.from(NOW.plusSeconds(SKEW_SECONDS - 1))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        // 过滤器只在无身份时验签：清掉上一请求建立的上下文，模拟新请求
        SecurityContextHolder.clearContext();
        doFilter(sign(baseClaims()
                .notBeforeTime(Date.from(NOW.plusSeconds(SKEW_SECONDS + 1))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("exp 过期在 skew 容差内（now-29s）仍接受，超过（now-31s）拒绝")
    void expBoundary_respectedBySkew() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(sign(baseClaims()
                .expirationTime(Date.from(NOW.minusSeconds(SKEW_SECONDS - 1))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();

        SecurityContextHolder.clearContext();
        doFilter(sign(baseClaims()
                .expirationTime(Date.from(NOW.minusSeconds(SKEW_SECONDS + 1))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("iss 或 aud 不匹配、aud 缺失一律拒绝")
    void issuerAudience_enforced() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(sign(baseClaims().issuer("evil-issuer").build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        doFilter(sign(baseClaims().audience(List.of("other-api")).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // 完全缺失 aud 声明
        doFilter(sign(new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth").subject("42")
                .claim("scope", "user").claim("subjectType", "user")
                .claim("sid", "session-1").claim("authVersion", 0L)
                .issueTime(Date.from(NOW.minusSeconds(1))).notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("缺少 sid 或 authVersion 的旧格式令牌拒绝")
    void sessionClaims_required() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(sign(new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth").audience(List.of("cloudmart-api")).subject("42")
                .claim("scope", "user").claim("subjectType", "user").claim("authVersion", 0L)
                .issueTime(Date.from(NOW.minusSeconds(1))).notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        doFilter(sign(new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth").audience(List.of("cloudmart-api")).subject("42")
                .claim("scope", "user").claim("subjectType", "user").claim("sid", "session-1")
                .issueTime(Date.from(NOW.minusSeconds(1))).notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900))).build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("缺失 scope 不再缺省为 user，拒绝建立身份")
    void missingScope_rejected() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(sign(new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth").audience(List.of("cloudmart-api")).subject("42")
                .claim("subjectType", "user").claim("sid", "session-1").claim("authVersion", 0L)
                .issueTime(Date.from(NOW.minusSeconds(1))).notBeforeTime(Date.from(NOW))
                .expirationTime(Date.from(NOW.plusSeconds(900))).build()));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("subjectType 与 scope 不一致、未知身份域 scope=service 拒绝")
    void identityDomainConsistency_enforced() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(sign(baseClaims().claim("subjectType", "admin").build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        doFilter(sign(baseClaims().claim("scope", "service").claim("subjectType", "service").build()));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("撤销检查器确认有效时按声明参数调用并建立身份")
    void revocationChecker_consultedWithTokenIdentity() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);

        doFilter(userToken());

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        verify(revocationChecker).isActive(eq("user"), eq("42"), eq("session-1"), eq(0L));
    }

    @Test
    @DisplayName("已撤销令牌（登出/禁用/改密）拒绝建立身份")
    void revokedToken_rejected() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(false);

        doFilter(userToken());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("撤销状态不可确认（Redis 故障）时 fail-closed 拒绝")
    void revocationStateUnavailable_failsClosed() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong()))
                .thenThrow(new AuthStateException("会话撤销状态校验不可用（fail-closed）",
                        new IllegalStateException("redis down")));

        doFilter(userToken());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("未装配撤销检查器的模块仅本地验签，不触发撤销查询")
    void noRevocationChecker_localVerificationOnly() throws Exception {
        UserJwtAuthenticationFilter bareFilter = new UserJwtAuthenticationFilter(properties, jwkSource);
        // bare 构造使用真实系统时钟，令牌必须按当前时间签发
        Instant base = Instant.now();
        String realClockToken = sign(new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth").audience(List.of("cloudmart-api")).subject("42")
                .claim("scope", "user").claim("subjectType", "user")
                .claim("sid", "session-1").claim("authVersion", 0L)
                .issueTime(Date.from(base)).notBeforeTime(Date.from(base))
                .expirationTime(Date.from(base.plusSeconds(900))).build());

        bareFilter.doFilter(request(realClockToken), new MockHttpServletResponse(), mock(FilterChain.class));

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        verifyNoInteractions(revocationChecker);
    }

    @Test
    @DisplayName("用户令牌建立 ROLE_USER 并强制改写 X-User-Id 为令牌主体")
    void userToken_roleUser_andForcedUserIdHeader() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        CapturingChain capturingChain = new CapturingChain();

        filter.doFilter(request(userToken()), new MockHttpServletResponse(), capturingChain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        assertThat(capturingChain.captured.getHeader("X-User-Id")).isEqualTo("42");
        assertThat(AdminSecurityContext.get()).isNull();
    }

    @Test
    @DisplayName("管理员令牌建立 ROLE_ADMIN 并在请求处理期间填充 AdminSecurityContext")
    void adminToken_roleAdmin_andAdminContext() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        ContextCapturingChain capturingChain = new ContextCapturingChain();
        String adminToken = sign(baseClaims()
                .subject("7")
                .claim("scope", "admin")
                .claim("subjectType", "admin")
                .claim("perms", "monitor:job:list,monitor:job:run")
                .claim("username", "ops")
                .claim("deptId", "3")
                .build());

        filter.doFilter(request(adminToken), new MockHttpServletResponse(), capturingChain);

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
    @DisplayName("请求结束后管理员上下文被清理，不发生 ThreadLocal 泄漏")
    void adminContext_clearedAfterRequest() throws Exception {
        when(revocationChecker.isActive(anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        String adminToken = sign(baseClaims()
                .subject("7")
                .claim("scope", "admin")
                .claim("subjectType", "admin")
                .build());

        filter.doFilter(request(adminToken), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(AdminSecurityContext.get()).isNull();
    }

    @Test
    @DisplayName("无 Authorization 头不建立身份")
    void noAuthorizationHeader_noIdentity() throws ServletException, IOException {
        doFilter(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(revocationChecker);
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
