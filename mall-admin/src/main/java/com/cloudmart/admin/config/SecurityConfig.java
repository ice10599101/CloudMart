package com.cloudmart.admin.config;

import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * mall-admin 安全配置（SEC-01）。
 *
 * <p>双轨说明：mall-admin 保留既有的管理员授权模型——AdminContextFilter 从网关
 * 注入头建立 {@code AdminSecurityContext}，AdminPermissionInterceptor 执行
 * {@code @RequiresAdmin/@RequiresPermission}，外部流量已由网关
 * AdminAuthGlobalFilter 强制管理员身份。本链路的职责是：</p>
 * <ul>
 *   <li>注册 ServiceTokenAuthenticationFilter：mall-auth 调用 /auth/**、/logs/**
 *       必须携带 admin:auth 服务令牌（建立 ROLE_INTERNAL，控制器 @PreAuthorize
 *       二次确认），替代已被移除的裸 X-Internal-Call 头校验；</li>
 *   <li>传输层放行其余路径（permitAll），业务级授权由拦截器承担。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;

    public SecurityConfig(ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter) {
        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/**").permitAll()
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
