package com.cloudmart.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

@Configuration
public class RsaKeyConfig {

    /**
     * SEC-03：生产环境（prod profile）必须提供受管 RSA 密钥，缺失即启动失败——
     * 随机密钥会让双实例/滚动重启时验签互不兼容，且重启即全量登录态失效。
     * 开发环境允许临时生成（带告警），仅用于本地联调。
     */
    @Bean
    public RSAKey rsaKey(RsaKeyProperties properties, org.springframework.core.env.Environment env) {
        boolean prod = java.util.Arrays.stream(env.getActiveProfiles())
                .anyMatch("prod"::equals);
        if (properties.getPrivateKey() != null && properties.getPublicKey() != null) {
            if (prod && properties.getKeyId() == null || prod && properties.getKeyId().isBlank()) {
                throw new IllegalStateException(
                        "SEC03：生产环境必须配置固定 auth.rsa.key-id（JWKS 轮换依赖稳定 kid）");
            }
            return loadFromProperties(properties);
        }
        if (prod) {
            throw new IllegalStateException(
                    "SEC03：生产环境必须配置受管 RSA 密钥（auth.rsa.public-key/private-key/key-id），"
                            + "禁止启动时随机生成——随机密钥将导致多实例验签失败与重启后全量登录态失效");
        }
        return generateNewKey();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private RSAKey loadFromProperties(RsaKeyProperties properties) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] publicKeyBytes = Base64.getDecoder().decode(
                    properties.getPublicKey().replaceAll("\\s", ""));
            byte[] privateKeyBytes = Base64.getDecoder().decode(
                    properties.getPrivateKey().replaceAll("\\s", ""));

            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(publicKeyBytes));
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(privateKeyBytes));

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(properties.getKeyId() != null ? properties.getKeyId() : UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load RSA key from properties", e);
        }
    }

    private RSAKey generateNewKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key", ex);
        }
    }

    @org.springframework.context.annotation.Configuration
    @ConfigurationProperties(prefix = "auth.rsa")
    public static class RsaKeyProperties {

        private String publicKey;
        private String privateKey;
        private String keyId;

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }
}
