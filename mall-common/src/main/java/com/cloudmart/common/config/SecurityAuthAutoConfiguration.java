package com.cloudmart.common.config;

import com.cloudmart.common.interceptor.AdminPermissionInterceptor;
import com.cloudmart.common.security.CloudmartSecurityProperties;
import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
import com.cloudmart.common.security.ServiceTokenFeignInterceptor;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * 统一身份边界自动装配（SEC-01）：向依赖 mall-common 的服务提供
 * <ul>
 *   <li>{@link UserJwtAuthenticationFilter}：用户/管理员 JWT 本地验签；</li>
 *   <li>{@link ServiceTokenAuthenticationFilter}：入站服务令牌校验；</li>
 *   <li>{@link ServiceTokenFeignInterceptor}：出站 Feign 服务令牌签名；</li>
 *   <li>{@link AdminPermissionInterceptor} 注册：使 @RequiresPermission /
 *       @RequiresAdmin 注解在 mall-job/mall-gen 等模块真正生效。</li>
 * </ul>
 *
 * <p>装配条件由 {@code cloudmart.security.*} 驱动：配置了 {@code service-id}
 * 才装配入站过滤器；mall-admin 自有管理员上下文与拦截器注册（且无需入站
 * JWT 验签），因此不配置 service-id，仅使用出站签名拦截器。</p>
 */
@AutoConfiguration
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(CloudmartSecurityProperties.class)
public class SecurityAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock cloudmartSecurityClock() {
        return Clock.systemUTC();
    }

    /** 出站签名拦截器：mall-admin 等仅出站场景也可用（未配置目标时不签名） */
    @Bean
    @ConditionalOnMissingBean(ServiceTokenFeignInterceptor.class)
    public ServiceTokenFeignInterceptor serviceTokenFeignInterceptor(
            CloudmartSecurityProperties properties, Clock clock) {
        return new ServiceTokenFeignInterceptor(properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "cloudmart.security.service-id")
    public UserJwtAuthenticationFilter userJwtAuthenticationFilter(
            CloudmartSecurityProperties properties) {
        return new UserJwtAuthenticationFilter(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "cloudmart.security.service-id")
    public ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter(
            CloudmartSecurityProperties properties, Clock clock) {
        return new ServiceTokenAuthenticationFilter(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminPermissionInterceptor adminPermissionInterceptor() {
        return new AdminPermissionInterceptor();
    }

    /**
     * 注册权限拦截器使 @RequiresPermission 生效；mall-admin 通过
     * {@code register-admin-permission-interceptor=false} 关闭后自行注册
     * （保留 /auth/** 等排除路径）。
     */
    @Bean
    @ConditionalOnProperty(name = "cloudmart.security.service-id")
    public WebMvcConfigurer cloudmartAdminPermissionInterceptorRegistrar(
            CloudmartSecurityProperties properties, AdminPermissionInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (!properties.isRegisterAdminPermissionInterceptor()) {
                    return;
                }
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }
}
