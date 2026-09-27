package com.cloudmart.common.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 出站 Feign 服务令牌签名拦截器（SEC-01）：按目标服务查
 * {@link CloudmartSecurityProperties#getOutboundScopes()} 得到能力域，
 * 以本服务为 iss、目标服务为 aud 签出短效令牌放入
 * {@link ServiceTokenCodec#HEADER_NAME}。
 *
 * <ul>
 *   <li>未配置能力域的目标不签名——公开端点无需服务身份；</li>
 *   <li>配置了能力域但密钥/服务标识缺失时抛出明确异常（fail loud），
 *       绝不降级为发送裸信任头；</li>
 *   <li>同一目标只告警一次，避免日志风暴。</li>
 * </ul>
 */
public class ServiceTokenFeignInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenFeignInterceptor.class);

    private final CloudmartSecurityProperties properties;
    private final Clock clock;
    private final AtomicBoolean warnedMissingSecret = new AtomicBoolean(false);

    public ServiceTokenFeignInterceptor(CloudmartSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public void apply(RequestTemplate template) {
        String targetService = template.feignTarget().name();
        String scope = properties.getOutboundScopes().get(targetService);
        if (scope == null || scope.isBlank()) {
            return;
        }
        if (!properties.isServiceTokenSigningAvailable()) {
            if (warnedMissingSecret.compareAndSet(false, true)) {
                log.error("[SEC01] 出站服务令牌不可用：cloudmart.security.service-id/service-token-secret "
                        + "未正确配置，对 {} 的调用将被拒绝。请注入 CLOUDMART_SERVICE_TOKEN_SECRET。", targetService);
            }
            throw new IllegalStateException("服务令牌签名不可用（service-id 或 service-token-secret 缺失），"
                    + "拒绝以无凭证方式调用 " + targetService);
        }
        String token = ServiceTokenCodec.sign(
                properties.getServiceId(),
                targetService,
                scope,
                Duration.ofSeconds(properties.getServiceTokenTtlSeconds()),
                properties.getServiceTokenSecret(),
                clock.instant());
        template.header(ServiceTokenCodec.HEADER_NAME, token);
    }
}
