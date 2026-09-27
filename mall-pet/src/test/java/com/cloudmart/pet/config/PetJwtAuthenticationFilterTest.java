package com.cloudmart.pet.config;

import com.cloudmart.common.constant.SecurityConstants;
import com.nimbusds.jose.JOSEObjectType;
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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SEC-01 用户 JWT 直验过滤器测试（T01/T03 服务端断言）：
 * 有效 RS256 令牌建立 ROLE_USER 并把 X-User-Id 权威化为验证 subject；
 * alg=none、坏签名、过期令牌一律不建立身份、不改写头。
 */
@DisplayName("PetJwtAuthenticationFilter 用户 JWT 验签")
class PetJwtAuthenticationFilterTest {

    private static final String KID = "test-kid";

    private PetJwtAuthenticationFilter filter;
    private RSASSASigner signer;
    private RSAKey publicJwk;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, com.nimbusds.jose.JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        publicJwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID(KID)
                .build();
        JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(publicJwk.toPublicJWK()));
        filter = new PetJwtAuthenticationFilter(jwkSource);
        signer = new RSASSASigner(keyPair.getPrivate());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String signedToken(String subject, Date expiration) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("scope", "user")
                .expirationTime(expiration)
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(signer);
        return jwt.serialize();
    }

    private MockHttpServletRequest request(String uri, String token, String spoofedUserId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (token != null) {
            request.addHeader("Authorization", "Bearer " + token);
        }
        if (spoofedUserId != null) {
            request.addHeader(SecurityConstants.USER_ID_HEADER, spoofedUserId);
        }
        return request;
    }

    private MockHttpServletResponse runFilter(MockHttpServletRequest request)
            throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return response;
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("有效令牌建立 ROLE_USER，principal=subject")
    void validToken_authenticated() throws Exception {
        String token = signedToken("1001", new Date(System.currentTimeMillis() + 60_000));
        runFilter(request("/me", token, null));

        Authentication auth = currentAuth();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("1001");
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals(PetJwtAuthenticationFilter.ROLE_USER));
    }

    @Test
    @DisplayName("外部伪造的 X-User-Id 被验证 subject 覆盖")
    void spoofedUserIdHeader_overriddenByVerifiedSubject() throws Exception {
        String token = signedToken("1001", new Date(System.currentTimeMillis() + 60_000));
        MockHttpServletRequest req = request("/me", token, "9999");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        jakarta.servlet.http.HttpServletRequest carried =
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(carried.getHeader(SecurityConstants.USER_ID_HEADER)).isEqualTo("1001");
    }

    @Test
    @DisplayName("alg=none 明文令牌不建立身份、不改写头")
    void unsignedToken_rejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("1001").build();
        // 未签名 compact JWS：header.payload.（签名为空）
        String payload = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String header = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String token = header + "." + payload + ".";

        MockHttpServletRequest req = request("/me", token, "9999");
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(currentAuth()).isNull();
        jakarta.servlet.http.HttpServletRequest carried =
                (jakarta.servlet.http.HttpServletRequest) chain.getRequest();
        assertThat(carried.getHeader(SecurityConstants.USER_ID_HEADER)).isEqualTo("9999");
    }

    @Test
    @DisplayName("坏签名令牌拒绝")
    void badSignature_rejected() throws Exception {
        // 用另一把密钥签名，公钥不匹配
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();
        RSASSASigner otherSigner = new RSASSASigner(otherPair.getPrivate());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("1001")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).type(JOSEObjectType.JWT).build(),
                claims);
        jwt.sign(otherSigner);

        runFilter(request("/me", jwt.serialize(), null));
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("过期令牌拒绝")
    void expiredToken_rejected() throws Exception {
        String token = signedToken("1001", new Date(System.currentTimeMillis() - 60_000));
        runFilter(request("/me", token, null));
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("无 Authorization 头保持匿名语义")
    void missingToken_anonymous() throws Exception {
        runFilter(request("/public/1001", null, null));
        assertThat(currentAuth()).isNull();
    }
}
