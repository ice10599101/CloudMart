package com.cloudmart.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一身份边界配置（SEC-01）：三种不可互换的主体 USER / ADMIN / SERVICE。
 *
 * <ul>
 *   <li>USER：用户 JWT（mall-auth RS256 签发，scope=user）由
 *       {@link UserJwtAuthenticationFilter} 本地验签后建立 ROLE_USER；</li>
 *   <li>ADMIN：管理员 JWT（scope=admin）由同一过滤器建立 ROLE_ADMIN，
 *       并填充 {@code AdminSecurityContext} 供 @RequiresPermission 生效；</li>
 *   <li>SERVICE：服务间调用必须携带 {@link ServiceTokenCodec#HEADER_NAME} 短期签名令牌，
 *       由 {@link ServiceTokenAuthenticationFilter} 按路径校验 iss/scope 后建立
 *       ROLE_INTERNAL。裸 {@code X-Internal-Call} 头不再构成任何信任凭证。</li>
 * </ul>
 *
 * <p>配置约定（前缀 {@code cloudmart.security}）：</p>
 * <ul>
 *   <li>{@code service-id}：本服务标识，同时是入站令牌的期望 aud 与出站令牌的 iss。
 *       未配置时入站过滤器不装配（如 mall-admin 自有认证模型，仅配置出站签名）；</li>
 *   <li>{@code service-token-secret}：全部服务共享的 HS256 密钥（≥32 字节），
 *       部署环境变量 {@code CLOUDMART_SERVICE_TOKEN_SECRET} 注入，禁止写入仓库；</li>
 *   <li>{@code service-token-paths}：入站路径 → (允许签发方, 能力域) 强映射，
 *       最长前缀优先；</li>
 *   <li>{@code outbound-scopes}：出站 Feign 调用 目标服务 → 能力域，
 *       {@link ServiceTokenFeignInterceptor} 据此对跨服务调用签名。</li>
 * </ul>
 *
 * <p>密钥缺失时入站服务令牌校验进入 fail-closed（内部/管理端点全部 401），
 * 出站签名在调用时抛出明确异常——绝不降级为发送裸信任头。</p>
 */
@ConfigurationProperties(prefix = "cloudmart.security")
public class CloudmartSecurityProperties {

    /** 入站服务令牌的期望受众 / 出站令牌的签发方标识（如 mall-order） */
    private String serviceId;

    /** 服务令牌共享密钥（HS256，≥32 字节；部署环境变量 CLOUDMART_SERVICE_TOKEN_SECRET） */
    private String serviceTokenSecret = "";

    /** mall-auth JWKS 地址（RS256 用户/管理员令牌验签公钥） */
    private String jwksUri = "http://127.0.0.1:9001/oauth2/jwks";

    /** 令牌校验时钟偏移容忍（秒） */
    private int clockSkewSeconds = 30;

    /** 是否启用入站用户/管理员 JWT 验签过滤器 */
    private boolean userJwtVerificationEnabled = true;

    /** 是否自动注册 AdminPermissionInterceptor（mall-admin 自行注册，须置 false 防止重复） */
    private boolean registerAdminPermissionInterceptor = true;

    /** 出站服务令牌有效期（秒），上限 300 */
    private int serviceTokenTtlSeconds = 60;

    /** 入站路径 → (允许签发方, 能力域) 强映射，最长前缀优先 */
    private List<ServiceTokenPath> serviceTokenPaths = new ArrayList<>();

    /** 出站 Feign 调用：目标服务名 → 能力域；未配置的目标不签名（公开端点无需令牌） */
    private Map<String, String> outboundScopes = new LinkedHashMap<>();

    /** 单条入站路径要求：前缀 + 允许的签发方列表 + 必需能力域 */
    public record ServiceTokenPath(String prefix, List<String> issuers, String scope) {
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getServiceTokenSecret() {
        return serviceTokenSecret;
    }

    public void setServiceTokenSecret(String serviceTokenSecret) {
        this.serviceTokenSecret = serviceTokenSecret;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public boolean isUserJwtVerificationEnabled() {
        return userJwtVerificationEnabled;
    }

    public void setUserJwtVerificationEnabled(boolean userJwtVerificationEnabled) {
        this.userJwtVerificationEnabled = userJwtVerificationEnabled;
    }

    public boolean isRegisterAdminPermissionInterceptor() {
        return registerAdminPermissionInterceptor;
    }

    public void setRegisterAdminPermissionInterceptor(boolean registerAdminPermissionInterceptor) {
        this.registerAdminPermissionInterceptor = registerAdminPermissionInterceptor;
    }

    public int getServiceTokenTtlSeconds() {
        return serviceTokenTtlSeconds;
    }

    public void setServiceTokenTtlSeconds(int serviceTokenTtlSeconds) {
        this.serviceTokenTtlSeconds = serviceTokenTtlSeconds;
    }

    public List<ServiceTokenPath> getServiceTokenPaths() {
        return serviceTokenPaths;
    }

    public void setServiceTokenPaths(List<ServiceTokenPath> serviceTokenPaths) {
        this.serviceTokenPaths = serviceTokenPaths;
    }

    public Map<String, String> getOutboundScopes() {
        return outboundScopes;
    }

    public void setOutboundScopes(Map<String, String> outboundScopes) {
        this.outboundScopes = outboundScopes;
    }

    /** 密钥是否可用于入站令牌校验（过滤器据此决定 fail-closed 拒绝全部令牌） */
    public boolean isServiceTokenValidationAvailable() {
        return serviceTokenSecret != null && serviceTokenSecret.length() >= ServiceTokenCodec.MIN_SECRET_LENGTH;
    }

    /** 密钥是否可用于出站签名 */
    public boolean isServiceTokenSigningAvailable() {
        return serviceId != null && !serviceId.isBlank() && isServiceTokenValidationAvailable();
    }
}
