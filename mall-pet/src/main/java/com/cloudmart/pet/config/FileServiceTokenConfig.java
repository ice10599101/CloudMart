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
 * mall-file 专用 Feign 配置（R04）：为发往 mall-file /internal/assets/** 的
 * 内部请求签名短期服务令牌，由 mall-file {@code ServiceTokenAuthenticationFilter}
 * 校验 iss=mall-pet、aud=mall-file、scope=file:internal。
 *
 * <p>与 {@link WishServiceTokenConfig} 同构：签发器惰性初始化，本地未注入密钥时
 * 服务可启动，首次调用才报明确错误。</p>
 *
 * <p>注意：Feign 客户端 configuration 类禁止标注 {@code @Configuration}，
 * 避免被组件扫描误挂到全部客户端。</p>
 */
public class FileServiceTokenConfig {

    private volatile ServiceTokenSigner cachedSigner;

    @Bean
    @Scope("prototype")
    public RequestInterceptor fileServiceTokenInterceptor(
            @Value("${pet.service-token.secret:${WISH_SERVICE_TOKEN_SECRET:}}") String secret) {
        return template -> template.header(ServiceTokenCodec.HEADER_NAME,
                signerFor(secret).sign("mall-file"));
    }

    private ServiceTokenSigner signerFor(String secret) {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-pet", "file:internal",
                    Duration.ofSeconds(60), Clock.systemUTC());
        }
        return cachedSigner;
    }
}
