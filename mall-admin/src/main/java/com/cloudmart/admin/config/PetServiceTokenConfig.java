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
 * mall-pet 专用 Feign 配置（SEC-01/SEC-02）：为发往 mall-pet 管理端（/admin/**）的内部请求
 * 签名短期服务令牌，由 mall-pet {@code PetServiceTokenAuthenticationFilter} 校验
 * iss=mall-admin、aud=mall-pet、scope=pet:admin。密钥默认复用 wish 服务令牌的
 * 部署环境变量（全套服务共享同一密钥）。
 *
 * <p>开发体验（与 wish 一致）：签发器惰性初始化——本地 IDE 未设置密钥时服务仍可启动
 * （其余功能不受影响），首次调用 mall-pet 才报明确错误；mall-pet 侧始终拒绝无令牌调用
 * （安全边界由服务端强制）。生产部署必须注入密钥，否则跨服务调用全部失败。</p>
 *
 * <p>注意：Feign 客户端 configuration 类禁止标注 {@code @Configuration}，
 * 避免被组件扫描误挂到全部客户端。</p>
 */
public class PetServiceTokenConfig {

    private volatile ServiceTokenSigner cachedSigner;

    @Bean
    @Scope("prototype")
    public RequestInterceptor petServiceTokenInterceptor(
            @Value("${pet.service-token.secret:${PET_SERVICE_TOKEN_SECRET:${WISH_SERVICE_TOKEN_SECRET:}}}")
            String secret) {
        return template -> {
            ServiceTokenSigner signer = signerFor(secret);
            // P0-3：把当前认证管理员的 username 签入令牌 claim（随签名防篡改），
            // mall-pet 从已验签声明读取审计操作者，请求头 X-Admin-Username 不再被信任
            com.cloudmart.common.context.AdminSecurityContext admin =
                    com.cloudmart.common.context.AdminSecurityContext.get();
            if (admin != null && admin.username() != null && !admin.username().isBlank()) {
                template.header(ServiceTokenCodec.HEADER_NAME, signer.sign("mall-pet",
                        java.util.Map.of("admin_username", admin.username())));
            } else {
                template.header(ServiceTokenCodec.HEADER_NAME, signer.sign("mall-pet"));
            }
        };
    }

    private ServiceTokenSigner signerFor(String secret) {
        if (cachedSigner == null) {
            cachedSigner = new ServiceTokenSigner(secret, "mall-admin", "pet:admin",
                    Duration.ofSeconds(60), Clock.systemUTC());
        }
        return cachedSigner;
    }
}
