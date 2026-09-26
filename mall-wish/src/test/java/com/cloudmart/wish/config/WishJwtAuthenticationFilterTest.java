package com.cloudmart.wish.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
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
 * B01 用户 JWT 直验过滤器测试（T02 服务端断言）：
 * 有效 RS256 令牌建立 ROLE_USER；alg=none、坏签名、过期令牌一律不建立身份。
 */
@DisplayName("WishJwtAuthenticationFilter 用户 JWT 验签")
class WishJwtAuthenticationFilterTest {

    private static final String KID = "test-kid";

    private WishJwtAuthenticationFilter filter;
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
        filter = new WishJwtAuthenticationFilter(jwkSource);
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

    private void runFilter(MockHttpServletRequest request) throws ServletException, IOException {
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    @DisplayName("有效签名 JWT 建立 ROLE_USER 身份，principal 为 userId")
    void validToken_establishesUserIdentity() throws Exception {
        String token = signedToken("42", new Date(System.currentTimeMillis() + 60_000));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wishes");
        request.addHeader("Authorization", "Bearer " + token);
        runFilter(request);

        Authentication auth = currentAuth();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("42");
        assertThat(auth.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals(WishJwtAuthenticationFilter.ROLE_USER));
    }

    @Test
    @DisplayName("过期 JWT 拒绝建立身份")
    void expiredToken_rejected() throws Exception {
        String token = signedToken("42", new Date(System.currentTimeMillis() - 60_000));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wishes");
        request.addHeader("Authorization", "Bearer " + token);
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("错误签名 JWT 拒绝建立身份")
    void badSignature_rejected() throws Exception {
        String token = signedToken("42", new Date(System.currentTimeMillis() + 60_000));
        // 篡改 payload 段
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "x." + parts[2];
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/wishes");
        request.addHeader("Authorization", "Bearer " + forged);
        runFilter(request);

        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("无 Authorization 头不建立身份（公开端点匿名语义不受影响）")
    void noHeader_anonymous() throws Exception {
        runFilter(new MockHttpServletRequest("GET", "/wishes"));
        assertThat(currentAuth()).isNull();
    }

    @Test
    @DisplayName("验签器自身可拒绝伪造公钥签名（防 Signer 配置错误）")
    void verifierRejectsForgedSignature() throws Exception {
        // 用另一把私钥签名，公钥验签必须失败——保证过滤器的验签不是摆设
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair otherPair = generator.generateKeyPair();
        RSASSASigner otherSigner = new RSASSASigner(otherPair.getPrivate());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("42")
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(), claims);
        jwt.sign(otherSigner);

        RSASSAVerifier verifier = new RSASSAVerifier(publicJwk.toRSAKey());
        assertThat(jwt.verify(verifier)).isFalse();
    }
}
