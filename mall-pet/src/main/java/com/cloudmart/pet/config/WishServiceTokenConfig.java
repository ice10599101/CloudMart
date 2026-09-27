package com.cloudmart.pet.config;

import com.cloudmart.common.security.ServiceTokenCodec;
import com.cloudmart.common.security.ServiceTokenSigner;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import java.time.Clock;
import java.time.Duration;

/**
 * mall-wish 专用 Feign 配置（B01）：为发往 mall-wish 宠物支持端点
 * （/internal/pet-support/**）的内部请求签名短期服务令牌，由 mall-wish
 * {@code ServiceTokenAuthenticationFilter} 校验 iss=mall-pet、aud=mall-wish、
 * scope=wish:pet。
 *
 * <p>开发体验（B20 补充）：签发器惰性初始化——本地 IDE 未设置
 * {@code WISH_SERVICE_TOKEN_SECRET} 时服务仍可启动，首次调用 mall-wish 才报
 * 明确错误；mall-wish 侧始终拒绝无令牌调用。</p>
 *
 * <p>注意：Feign 客户端 configuration 类禁止标注 {@code @Configuration}，
 * 避免被组件扫描误挂到全部客户端。</p>
 */
public class WishServiceTokenConfig {

    private volatile ServiceTokenSigner cachedSigner;

    @Bean
    @Scope("prototype")
    public RequestInterceptor wishServiceTokenInterceptor(
            @Value("${wish.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret) {
        return template -> template.header(ServiceTokenCodec.HEADER_NAME,
                signerFor(secret).sign("mall-wish"));
    }

    private ServiceTokenSigner signerFor(String secret) {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-pet", "wish:pet",
                    Duration.ofSeconds(60), Clock.systemUTC());
        }
        return cachedSigner;
    }
}
