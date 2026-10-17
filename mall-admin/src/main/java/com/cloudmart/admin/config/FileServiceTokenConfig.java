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
 * mall-file 专用 Feign 配置（ADMIN-04）：为发往 mall-file /internal/assets/admin/** 的
 * 内部请求签名短期服务令牌，由 mall-file {@code ServiceTokenAuthenticationFilter} 校验
 * iss=mall-admin、aud=mall-file、scope=file:internal。
 *
 * <p>mall-file 的 service-token-paths 已放行 mall-admin 签发方；未注入
 * CLOUDMART_SERVICE_TOKEN_SECRET 时首次调用才报明确错误（与 Wish/Pet 配置一致）。</p>
 *
 * <p>注意：Feign 客户端 configuration 类禁止标注 {@code @Configuration}，
 * 避免被组件扫描误挂到全部客户端。</p>
 */
public class FileServiceTokenConfig {

    private volatile ServiceTokenSigner cachedSigner;

    @Bean
    @Scope("prototype")
    public RequestInterceptor fileServiceTokenInterceptor(
            @Value("${file.service-token.secret:${CLOUDMART_SERVICE_TOKEN_SECRET:}}") String secret) {
        return template -> template.header(ServiceTokenCodec.HEADER_NAME,
                signerFor(secret).sign("mall-file"));
    }

    private ServiceTokenSigner signerFor(String secret) {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-admin", "file:internal",
                    Duration.ofSeconds(60), Clock.systemUTC());
        }
        return cachedSigner;
    }
}
