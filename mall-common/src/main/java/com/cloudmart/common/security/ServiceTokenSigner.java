package com.cloudmart.common.security;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

/**
 * 服务令牌签发器（B01，调用方侧）：为本服务签出短效 HS256 令牌。
 *
 * <p>与 {@link ServiceTokenCodec} 配套：接收方（如 mall-wish）按路径强校验
 * iss/aud/scope；调用方只对自己被授权的能力域签发令牌。密钥通过部署环境变量
 * {@code WISH_SERVICE_TOKEN_SECRET} 注入，缺失时构造失败（fail fast），
 * 不允许服务在无法签名的情况下带着"裸头信任"继续运行。</p>
 */
public final class ServiceTokenSigner {

    private final String secret;
    private final String issuer;
    private final String scope;
    private final Duration ttl;
    private final Clock clock;

    public ServiceTokenSigner(String secret, String issuer, String scope, Duration ttl, Clock clock) {
        if (secret == null || secret.length() < ServiceTokenCodec.MIN_SECRET_LENGTH) {
            throw new IllegalStateException("服务令牌密钥缺失或弱于 " + ServiceTokenCodec.MIN_SECRET_LENGTH
                    + " 字节：请通过部署环境变量 WISH_SERVICE_TOKEN_SECRET 注入");
        }
        Objects.requireNonNull(issuer, "issuer 不能为空");
        Objects.requireNonNull(scope, "scope 不能为空");
        Objects.requireNonNull(ttl, "ttl 不能为空");
        if (ttl.isNegative() || ttl.isZero() || ttl.toSeconds() > 300) {
            throw new IllegalArgumentException("服务令牌 ttl 必须在 (0, 300s] 内：短效是安全边界的一部分");
        }
        this.secret = secret;
        this.issuer = issuer;
        this.scope = scope;
        this.ttl = ttl;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /** 为目标服务签出一个新令牌。 */
    public String sign(String audience) {
        return ServiceTokenCodec.sign(issuer, audience, scope, ttl, secret, clock.instant());
    }
}
