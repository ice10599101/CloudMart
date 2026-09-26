package com.cloudmart.common.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * 内部服务间调用令牌（B01）：调用方自签、接收方本地校验的短期 HS256 JWT。
 *
 * <p>替代"裸 {@code X-Internal-Call} 头即可信"的旧模型：用户 JWT 永远不能获得
 * INTERNAL 角色，服务身份只能由持有共享密钥签出的短效令牌建立。令牌声明包含
 * {@code iss}（serviceId）、{@code aud}（目标服务）、{@code scope}（能力域）、
 * {@code exp}/{@code iat}（短有效期，建议 ≤120s）。</p>
 *
 * <p>实现说明：为避免给 mall-common 引入新的 JWT 依赖，这里直接按 RFC 7515
 * 生成/校验 compact JWS（HS256 = HMAC-SHA256），签名校验使用常量时间比较。
 * 密钥通过部署环境注入（{@code WISH_SERVICE_TOKEN_SECRET}），禁止写入仓库。</p>
 */
public final class ServiceTokenCodec {

    /** 服务令牌请求头名。接收方按路径强校验 iss/scope，用户请求无需携带。 */
    public static final String HEADER_NAME = "X-Service-Token";

    private static final String ALG = "HS256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 最短密钥长度：HS256 要求 ≥32 字节，防止弱密钥上线。 */
    public static final int MIN_SECRET_LENGTH = 32;

    private ServiceTokenCodec() {
    }

    /**
     * 签发服务令牌。
     *
     * @param issuer       调用方服务标识（如 mall-admin/mall-job/mall-pet）
     * @param audience     目标服务标识（如 mall-wish）
     * @param scope        能力域（如 wish:admin/wish:jobs/wish:pet）
     * @param ttl          有效期；接收方另有时钟偏移容忍
     * @param secret       共享密钥（≥{@link #MIN_SECRET_LENGTH} 字节）
     * @param now          签发时间（由调用方注入 Clock，便于测试）
     * @return compact JWS（header.payload.signature）
     */
    public static String sign(String issuer, String audience, String scope,
                              Duration ttl, String secret, Instant now) {
        requireSecret(secret);
        Objects.requireNonNull(issuer, "issuer 不能为空");
        Objects.requireNonNull(audience, "audience 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        Objects.requireNonNull(ttl, "ttl 不能为空");
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("服务令牌 ttl 必须为正");
        }

        long iat = now.getEpochSecond();
        long exp = now.plus(ttl).getEpochSecond();
        String jti = new java.math.BigInteger(64, RANDOM).toString(36);

        String payload = "{\"iss\":\"" + escape(issuer) + "\",\"aud\":\"" + escape(audience)
                + "\",\"scope\":\"" + escape(scope) + "\",\"iat\":" + iat
                + ",\"exp\":" + exp + ",\"jti\":\"" + jti + "\"}";
        String header = "{\"alg\":\"" + ALG + "\",\"typ\":\"JWT\"}";

        String signingInput = ENCODER.encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + hmac(signingInput, secret);
    }

    /**
     * 校验服务令牌；任何一步失败都抛出 {@link ServiceTokenException}，绝不降级放行。
     *
     * @param token           令牌原文
     * @param secret          共享密钥
     * @param expectedAudience 期望受众（本服务）
     * @param expectedIssuer   期望签发方；null 表示不限定（仍由调用方基于 iss 做 allowlist 判断）
     * @param expectedScope    期望能力域；null 表示不校验
     * @param now              当前时间（由接收方注入 Clock）
     * @param clockSkew        时钟偏移容忍
     * @return 已校验的声明（iss/aud/scope/exp）
     */
    public static ServiceTokenClaims verify(String token, String secret, String expectedAudience,
                                            String expectedIssuer, String expectedScope,
                                            Instant now, Duration clockSkew) throws ServiceTokenException {
        requireSecret(secret);
        if (token == null || token.isBlank()) {
            throw new ServiceTokenException("令牌为空", ServiceTokenError.MALFORMED);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new ServiceTokenException("令牌段数非法", ServiceTokenError.MALFORMED);
        }

        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = hmacBytes(signingInput, secret);
        byte[] actualSignature;
        try {
            actualSignature = DECODER.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new ServiceTokenException("签名 base64 非法", ServiceTokenError.MALFORMED, e);
        }
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new ServiceTokenException("签名校验失败", ServiceTokenError.BAD_SIGNATURE);
        }

        JsonNode claims;
        try {
            claims = MAPPER.readTree(DECODER.decode(parts[1]));
        } catch (Exception e) {
            throw new ServiceTokenException("载荷解析失败", ServiceTokenError.MALFORMED, e);
        }

        String headerAlg = readHeaderAlg(parts[0]);
        if (!ALG.equals(headerAlg)) {
            throw new ServiceTokenException("算法不允许: " + headerAlg, ServiceTokenError.MALFORMED);
        }

        String aud = text(claims, "aud");
        if (!Objects.equals(aud, expectedAudience)) {
            throw new ServiceTokenException("audience 不匹配", ServiceTokenError.WRONG_AUDIENCE);
        }
        String iss = text(claims, "iss");
        if (expectedIssuer != null && !Objects.equals(iss, expectedIssuer)) {
            throw new ServiceTokenException("issuer 不匹配", ServiceTokenError.WRONG_ISSUER);
        }
        String scope = text(claims, "scope");
        if (expectedScope != null && !Objects.equals(scope, expectedScope)) {
            throw new ServiceTokenException("scope 不匹配", ServiceTokenError.WRONG_SCOPE);
        }

        long skew = clockSkew.toSeconds();
        long exp = claims.path("exp").asLong(-1);
        if (exp < 0) {
            throw new ServiceTokenException("缺少 exp", ServiceTokenError.MALFORMED);
        }
        if (now.getEpochSecond() - skew >= exp) {
            throw new ServiceTokenException("令牌已过期", ServiceTokenError.EXPIRED);
        }
        long iat = claims.path("iat").asLong(-1);
        if (iat >= 0 && iat - skew > now.getEpochSecond()) {
            throw new ServiceTokenException("iat 在未来", ServiceTokenError.MALFORMED);
        }
        return new ServiceTokenClaims(iss, aud, scope, exp);
    }

    private static String readHeaderAlg(String headerSegment) throws ServiceTokenException {
        try {
            JsonNode header = MAPPER.readTree(DECODER.decode(headerSegment));
            return header.path("alg").asText(null);
        } catch (Exception e) {
            throw new ServiceTokenException("头部解析失败", ServiceTokenError.MALFORMED, e);
        }
    }

    private static String text(JsonNode claims, String field) {
        String value = claims.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String hmac(String signingInput, String secret) {
        return ENCODER.encodeToString(hmacBytes(signingInput, secret));
    }

    private static byte[] hmacBytes(String signingInput, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 初始化失败", e);
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void requireSecret(String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("服务令牌密钥缺失或弱于 " + MIN_SECRET_LENGTH
                    + " 字节：请通过部署环境变量 WISH_SERVICE_TOKEN_SECRET 注入");
        }
    }

    /** 已校验令牌的声明集。 */
    public record ServiceTokenClaims(String issuer, String audience, String scope, long exp) {
    }

    /** 校验失败原因，机器可读，用于日志与监控区分攻击类型。 */
    public enum ServiceTokenError {
        MALFORMED, BAD_SIGNATURE, EXPIRED, WRONG_AUDIENCE, WRONG_ISSUER, WRONG_SCOPE
    }

    /** 令牌校验失败异常。 */
    public static final class ServiceTokenException extends Exception {

        private final ServiceTokenError error;

        public ServiceTokenException(String message, ServiceTokenError error) {
            super(message);
            this.error = error;
        }

        public ServiceTokenException(String message, ServiceTokenError error, Throwable cause) {
            super(message, cause);
            this.error = error;
        }

        public ServiceTokenError getError() {
            return error;
        }
    }
}
