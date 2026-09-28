package com.cloudmart.gateway.filter;

import com.cloudmart.gateway.security.SessionValidator;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关身份链路语义（SEC-01/03）：
 * 1. 携带完整合法声明且会话有效时注入 X-User-Id / X-Admin-*；
 * 2. alg=none、错误签名、过期、缺 sid/authVersion、iss/aud 不匹配、身份域非法一律拒绝；
 * 3. 会话被撤销或版本不匹配 → 401 信封（禁用/踢人秒级失效）；
 * 4. 会话校验服务异常 → fail-closed 401；
 * 5. 客户端伪造的身份头被剥离，且不再注入 X-Internal-Call。
 */
class JwtAuthenticationFilterTest {

    private static final String KID = "test-kid";
    private static final String SID = "session-abc";

    private JwtAuthenticationFilter filter;
    private JWSSigner signer;
    private RSAKey rsaKey;
    private ServerWebExchange exchange;
    private final Map<String, String> sessionStore = new HashMap<>();
    /** 主体当前认证状态版本（键 user:1 / admin:7，缺省视为 0） */
    private final Map<String, String> subjectVersionStore = new HashMap<>();

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, com.nimbusds.jose.JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .keyID(KID)
                .build();
        signer = new RSASSASigner(rsaKey);
        sessionStore.clear();
        subjectVersionStore.clear();
        sessionStore.put(SID, "0");
        filter = new JwtAuthenticationFilter(new ImmutableJWKSet<>(new JWKSet(rsaKey)), stubValidator());
    }

    /** 以内存 Map 模拟会话账本与主体版本账本（与 SessionValidator 同一语义） */
    private SessionValidator stubValidator() {
        return new SessionValidator(null, "auth:session_valid:") {
            @Override
            public Mono<Boolean> isSessionValid(String subjectType, String subjectId,
                                                String sid, String expectedVersion) {
                String sessionVersion = sessionStore.get(sid);
                if (sessionVersion == null || !sessionVersion.equals(expectedVersion)) {
                    return Mono.just(false);
                }
                String currentVersion = subjectVersionStore.get(subjectType + ":" + subjectId);
                return Mono.just((currentVersion == null ? "0" : currentVersion).equals(expectedVersion));
            }
        };
    }

    private String signedToken(String subject, Date expiration) {
        return signedToken(subject, expiration, "user", SID, 0L);
    }

    private String signedToken(String subject, Date expiration, String subjectType, String sid, long authVersion) {
        try {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("cloudmart-auth")
                    .audience(List.of("cloudmart-api"))
                    .subject(subject)
                    .claim("scope", subjectType)
                    .claim("subjectType", subjectType)
                    .claim("sid", sid)
                    .claim("authVersion", authVersion)
                    .issueTime(new Date(now))
                    .notBeforeTime(new Date(now - 1000))
                    .expirationTime(expiration)
                    .build();
            SignedJWT signed = new SignedJWT(gatewayFilterHeader(), claims);
            signed.sign(signer);
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JWSHeader gatewayFilterHeader() {
        return new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(KID)
                .build();
    }

    private Date inOneMinute() {
        return new Date(System.currentTimeMillis() + 60_000);
    }

    /** 用另一密钥对签发（kid 不匹配 + 签名必然错误） */
    private String forgedSignatureToken(String subject) throws NoSuchAlgorithmException, com.nimbusds.jose.JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey otherKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey(pair.getPrivate())
                .keyID("other-kid")
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject).build();
        SignedJWT signed = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("other-kid").build(),
                claims);
        signed.sign(new RSASSASigner(otherKey));
        return signed.serialize();
    }

    private void run(org.springframework.mock.http.server.reactive.MockServerHttpRequest request) {
        exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = ex -> {
            exchange = ex;
            return Mono.empty();
        };
        filter.filter(exchange, chain).block();
    }

    @Test
    void validToken_withLiveSession_injectsUserId() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", inOneMinute()))
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isNull();
    }

    @Test
    void validToken_adminScope_injectsAdminRoleHeader() {
        run(MockServerHttpRequest.get("/api/admin/users")
                .header("Authorization", "Bearer " + signedToken("7", inOneMinute(), "admin", SID, 0L))
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("7");
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Admin-Role")).isEqualTo("admin");
    }

    @Test
    void missingSidClaim_rejected() throws Exception {
        // 旧格式令牌（无 sid/authVersion）：必须重新登录，不得放行
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("cloudmart-auth").audience(List.of("cloudmart-api"))
                .subject("1").claim("scope", "user").claim("subjectType", "user")
                .issueTime(new Date(now)).expirationTime(inOneMinute())
                .build();
        SignedJWT signed = new SignedJWT(gatewayFilterHeader(), claims);
        signed.sign(signer);

        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signed.serialize())
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void revokedSession_returns401() {
        sessionStore.remove(SID);
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", inOneMinute()))
                .build());
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void authVersionMismatch_returns401() {
        // 版本递增后（禁用/改密），旧令牌立即失效
        sessionStore.put(SID, "1");
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", inOneMinute(), "user", SID, 0L))
                .build());
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void subjectVersionIncremented_returns401() {
        // 主体当前版本递增（invalidate）而会话记录仍为旧版本：旧令牌必须失效
        subjectVersionStore.put("user:1", "1");
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", inOneMinute(), "user", SID, 0L))
                .build());
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void sessionValidatorFailure_failsClosedWith401() throws Exception {
        JwtAuthenticationFilter failClosedFilter = new JwtAuthenticationFilter(
                new ImmutableJWKSet<>(new JWKSet(rsaKey)), new SessionValidator(null, "p:") {
                    @Override
                    public Mono<Boolean> isSessionValid(String subjectType, String subjectId,
                                                        String sid, String expectedVersion) {
                        return Mono.error(new IllegalStateException("redis down"));
                    }
                });
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", inOneMinute()))
                .build());
        failClosedFilter.filter(exchange, ex -> Mono.empty()).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void wrongIssuer_rejected() throws Exception {
        long now = System.currentTimeMillis();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("evil-issuer").audience(List.of("cloudmart-api"))
                .subject("1").claim("scope", "user").claim("subjectType", "user")
                .claim("sid", SID).claim("authVersion", 0L)
                .issueTime(new Date(now)).notBeforeTime(new Date(now - 1000)).expirationTime(inOneMinute())
                .build();
        SignedJWT signed = new SignedJWT(gatewayFilterHeader(), claims);
        signed.sign(signer);
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signed.serialize())
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void identityDomainMismatch_rejected() {
        // scope 与 subjectType 不一致：身份域被篡改的令牌
        String token;
        try {
            long now = System.currentTimeMillis();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("cloudmart-auth").audience(List.of("cloudmart-api"))
                    .subject("1").claim("scope", "admin").claim("subjectType", "user")
                    .claim("sid", SID).claim("authVersion", 0L)
                    .issueTime(new Date(now)).notBeforeTime(new Date(now - 1000))
                    .expirationTime(inOneMinute())
                    .build();
            SignedJWT signed = new SignedJWT(gatewayFilterHeader(), claims);
            signed.sign(signer);
            token = signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + token)
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void identityRequiredSubPath_withoutToken_noIdentityAndNoInternalCall() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts").build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Internal-Call")).isNull();
    }

    @Test
    void publicPath_withExpiredToken_rejects() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", new Date(System.currentTimeMillis() - 60_000)))
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void publicPath_withForgedSignature_rejects() throws NoSuchAlgorithmException, com.nimbusds.jose.JOSEException {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + forgedSignatureToken("1"))
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void publicPath_withUnsignedPlainJwt_rejects() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("1").build();
        com.nimbusds.jwt.PlainJWT plain = new com.nimbusds.jwt.PlainJWT(claims);
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + plain.serialize())
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isNull();
    }

    @Test
    void stripsClientForgedIdentityHeaders() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", inOneMinute()))
                .header("X-User-Id", "999")
                .header("X-Internal-Call", "true")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
    }
}
