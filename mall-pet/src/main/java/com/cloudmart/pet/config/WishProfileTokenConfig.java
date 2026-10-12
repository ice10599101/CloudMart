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
 * R22 mall-wish 用户画像端点（/internal/users/**）的 Feign 令牌配置：
 * iss=mall-pet、scope=wish:profile，与宠物支持端点（wish:pet）最小权限隔离。
 * 禁止标注 @Configuration（避免挂到全部 Feign 客户端）。
 */
public class WishProfileTokenConfig {

    private volatile ServiceTokenSigner cachedSigner;

    @Bean
    @Scope("prototype")
    public RequestInterceptor wishProfileServiceTokenInterceptor(
            @Value("${wish.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret) {
        return template -> template.header(ServiceTokenCodec.HEADER_NAME,
                signerFor(secret).sign("mall-wish"));
    }

    private ServiceTokenSigner signerFor(String secret) {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-pet", "wish:profile",
                    Duration.ofSeconds(60), Clock.systemUTC());
        }
        return cachedSigner;
    }
}
