package com.cloudmart.admin.config;

import com.cloudmart.common.security.ServiceTokenCodec;
import com.cloudmart.common.security.ServiceTokenSigner;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;

import java.time.Clock;
import java.time.Duration;

/**
 * mall-wish 专用 Feign 配置（B01）：为发往 mall-wish 管理端（/admin/**）的内部请求
 * 签名短期服务令牌，由 mall-wish {@code ServiceTokenAuthenticationFilter} 校验
 * iss=mall-admin、aud=mall-wish、scope=wish:admin。
 *
 * <p>注意：Feign 客户端 configuration 类禁止标注 {@code @Configuration}，
 * 避免被组件扫描误挂到全部客户端。</p>
 */
public class WishServiceTokenConfig {

    @Bean
    @Scope("prototype")
    public RequestInterceptor wishServiceTokenInterceptor(
            @Value("${wish.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret) {
        ServiceTokenSigner signer = new ServiceTokenSigner(secret, "mall-admin", "wish:admin",
                Duration.ofSeconds(60), Clock.systemUTC());
        return template -> template.header(ServiceTokenCodec.HEADER_NAME, signer.sign("mall-wish"));
    }
}
