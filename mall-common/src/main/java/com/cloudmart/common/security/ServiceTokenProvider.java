package com.cloudmart.common.security;

import java.time.Clock;
import java.time.Duration;

/**
 * 服务令牌签发器（SEC-01，RestClient/WebClient 等非 Feign 调用方使用）：
 * 以本服务为 iss、目标服务为 aud、指定能力域签出短效令牌。
 *
 * <p>Feign 调用统一由 {@link ServiceTokenFeignInterceptor} 自动签名；
 * {@code RestClient} 等手工构造的调用注入本组件，按目标+能力域签名后放入
 * {@link ServiceTokenCodec#HEADER_NAME} 请求头。</p>
 *
 * <p>密钥或服务标识缺失时抛出明确异常（fail loud），绝不降级为发送裸信任头。</p>
 */
public class ServiceTokenProvider {

    private final CloudmartSecurityProperties properties;
    private final Clock clock;

    public ServiceTokenProvider(CloudmartSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 为目标服务签出一个新令牌。
     *
     * @param targetService 目标服务标识（aud，如 mall-product）
     * @param scope         能力域（如 product:read）
     * @return compact JWS
     */
    public String sign(String targetService, String scope) {
        if (!properties.isServiceTokenSigningAvailable()) {
            throw new IllegalStateException("服务令牌签名不可用（service-id 或 service-token-secret 缺失），"
                    + "拒绝以无凭证方式调用 " + targetService
                    + "。请注入环境变量 CLOUDMART_SERVICE_TOKEN_SECRET。");
        }
        return ServiceTokenCodec.sign(
                properties.getServiceId(),
                targetService,
                scope,
                Duration.ofSeconds(properties.getServiceTokenTtlSeconds()),
                properties.getServiceTokenSecret(),
                clock.instant());
    }
}
