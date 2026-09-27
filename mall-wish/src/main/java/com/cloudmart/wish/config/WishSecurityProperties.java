package com.cloudmart.wish.config;

import com.cloudmart.common.security.ServiceTokenCodec;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * B01 身份边界配置。
 *
 * <p>{@code serviceTokenSecret} 必须通过部署环境变量 {@code WISH_SERVICE_TOKEN_SECRET}
 * 注入（与 mall-admin/mall-job/mall-pet 共享同一密钥），缺失时服务拒绝启动：
 * 没有密钥意味着服务令牌无法校验，任何内部端点都不允许在无校验状态下开放。</p>
 */
@Getter
@Setter
@Slf4j
@Component
@ConfigurationProperties(prefix = "wish.security")
public class WishSecurityProperties {

    /** 服务令牌共享密钥（HS256，≥32 字节；部署环境变量 WISH_SERVICE_TOKEN_SECRET） */
    private String serviceTokenSecret = "";

    /** mall-auth JWKS 地址（RS256 用户令牌验签公钥；与网关一致） */
    private String jwksUri = "http://127.0.0.1:9001/oauth2/jwks";

    /** 令牌校验时钟偏移容忍（秒） */
    private int clockSkewSeconds = 30;

    public WishSecurityProperties() {
    }

    /** 测试/编程式构造（B01 fail-fast 校验同生产路径） */
    public WishSecurityProperties(String serviceTokenSecret, String jwksUri, int clockSkewSeconds) {
        this.serviceTokenSecret = serviceTokenSecret;
        this.jwksUri = jwksUri;
        this.clockSkewSeconds = clockSkewSeconds;
        validate();
    }

    /**
     * B20 开发体验：密钥缺失不阻塞启动——服务照常服务公开/用户端点；
     * 服务令牌过滤器进入"全部拒绝"模式（内部/管理端点 401），安全边界仍是
     * fail-closed（绝不放行未验签调用）。生产部署必须注入密钥，否则跨服务
     * 调用全部失败（调用方签发器同样缺失会报明确错误）。
     */
    @jakarta.annotation.PostConstruct
    void validate() {
        if (serviceTokenSecret == null || serviceTokenSecret.length() < ServiceTokenCodec.MIN_SECRET_LENGTH) {
            log.warn("B01：wish.security.service-token-secret 缺失或弱于 {} 字节——"
                            + "服务令牌校验不可用，内部/管理端点将拒绝所有调用。"
                            + "请注入环境变量 WISH_SERVICE_TOKEN_SECRET（生产必须）",
                    ServiceTokenCodec.MIN_SECRET_LENGTH);
        }
        if (jwksUri == null || jwksUri.isBlank()) {
            throw new IllegalStateException("wish.security.jwks-uri 未配置：无法验签用户 JWT");
        }
        if (clockSkewSeconds < 0) {
            throw new IllegalStateException("wish.security.clock-skew-seconds 不能为负");
        }
    }

    /** 密钥是否可用于令牌校验（过滤器据此决定 fail-closed 拒绝全部令牌） */
    public boolean isServiceTokenValidationAvailable() {
        return serviceTokenSecret != null && serviceTokenSecret.length() >= ServiceTokenCodec.MIN_SECRET_LENGTH;
    }
}
