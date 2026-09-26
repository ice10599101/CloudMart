package com.cloudmart.wish.config;

import com.cloudmart.common.security.ServiceTokenCodec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * B01 身份边界配置。
 *
 * <p>{@code serviceTokenSecret} 必须通过部署环境变量 {@code WISH_SERVICE_TOKEN_SECRET}
 * 注入（与 mall-admin/mall-job/mall-pet 共享同一密钥），缺失时服务拒绝启动：
 * 没有密钥意味着服务令牌无法校验，任何内部端点都不允许在无校验状态下开放。</p>
 *
 * @param serviceTokenSecret 服务令牌共享密钥（HS256，≥32 字节）
 * @param jwksUri            mall-auth JWKS 地址（RS256 用户令牌验签公钥）
 * @param clockSkewSeconds   令牌校验时钟偏移容忍
 */
@ConfigurationProperties(prefix = "wish.security")
public record WishSecurityProperties(
        String serviceTokenSecret,
        String jwksUri,
        int clockSkewSeconds) {

    public WishSecurityProperties {
        if (serviceTokenSecret == null || serviceTokenSecret.length() < ServiceTokenCodec.MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "wish.security.service-token-secret 缺失或弱于 " + ServiceTokenCodec.MIN_SECRET_LENGTH
                            + " 字节：请通过部署环境变量 WISH_SERVICE_TOKEN_SECRET 注入（B01 fail fast）");
        }
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("wish.security.jwks-uri 未配置：无法验签用户 JWT");
        }
        if (clockSkewSeconds < 0) {
            throw new IllegalStateException("wish.security.clock-skew-seconds 不能为负");
        }
    }
}
