package com.cloudmart.gateway.filter;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定网关身份注入与验签关键语义：
 * 1. 携带有效签名 JWT 时注入 X-User-Id（/posts/drafts 这类需登录身份的接口依赖此语义）
 * 2. alg=none 明文 JWT、错误签名、过期 token 一律拒绝注入（防伪造/防过期 token 永久有效）
 * 3. 客户端伪造的身份头被剥离
 * 4. /posts/drafts、/posts/liked 不算公开路径：无 token 时不得发放 X-Internal-Call（服务端须 401）
 */
class JwtAuthenticationFilterTest {

    private static final String KID = "test-kid";

    private JwtAuthenticationFilter filter;
    private JWSSigner signer;
    private ServerWebExchange exchange;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, com.nimbusds.jose.JOSEException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey(keyPair.getPrivate())
                .keyID(KID)
                .build();
        signer = new RSASSASigner(rsaKey);
        filter = new JwtAuthenticationFilter(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
    }

    private String signedToken(String subject, Date expiration) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .claim("scope", "user")
                    .expirationTime(expiration)
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(KID)
                    .build();
            SignedJWT signed = new SignedJWT(header, claims);
            signed.sign(signer);
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(subject).build();
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("other-kid").build(),
                    claims);
            signed.sign(new RSASSASigner(otherKey));
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
    void publicPath_withValidSignedToken_injectsUserId() {
        run(MockServerHttpRequest.get("/api/community/posts/drafts")
                .header("Authorization", "Bearer " + signedToken("1", new Date(System.currentTimeMillis() + 60_000)))
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
    }

    @Test
    void identityRequiredSubPath_withValidSignedToken_injectsUserId() {
        run(MockServerHttpRequest.get("/api/community/posts/liked")
                .header("Authorization", "Bearer " + signedToken("1", new Date(System.currentTimeMillis() + 60_000)))
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
    }

    @Test
    void identityRequiredSubPath_withoutToken_noIdentityAndNoInternalCall() {
        // /drafts、/liked 语义私有：匿名访问不得携带 X-Internal-Call，服务端必须 401 而非放行为匿名
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
        // alg=none 明文 JWT：零门槛伪造向量，必须拒绝
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
                .header("Authorization", "Bearer " + signedToken("1", new Date(System.currentTimeMillis() + 60_000)))
                .header("X-User-Id", "999")
                .header("X-Internal-Call", "true")
                .build());
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1");
    }
}
