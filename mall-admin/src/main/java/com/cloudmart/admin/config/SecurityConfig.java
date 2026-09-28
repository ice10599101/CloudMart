package com.cloudmart.admin.config;

import com.cloudmart.common.security.JsonAuthenticationEntryPoint;
import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * mall-admin 安全配置（SEC-01/03）。
 *
 * <p>信任边界：身份只能由 mall-auth 签发的 RS256 JWT 本地验签建立
 * （{@link UserJwtAuthenticationFilter}：scope=admin → ROLE_ADMIN 并填充
 * AdminSecurityContext；scope=user → ROLE_USER）。网关注入的
 * {@code X-Admin-*} 头不再是身份源——直连管理服务时无法借自填头冒充管理员。
 * 服务间调用（mall-auth 查管理员凭据/记登录日志）由
 * {@link ServiceTokenAuthenticationFilter} 建立 ROLE_INTERNAL，
 * 控制器 @PreAuthorize 二次确认。</p>
 *
 * <p>授权默认值（SEC-03）：匿名默认拒绝；管理接口默认仅 ROLE_ADMIN，
 * 方法级 {@code @RequiresAdmin/@RequiresPermission} 继续由拦截器细粒度收口。
 * 内部 /auth/**、/logs/** 仅接受服务令牌。诊断接口只注册在开发 profile
 * （{@link FeignDiagnosticController}），生产 404。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;
    private final UserJwtAuthenticationFilter userJwtAuthenticationFilter;

    public SecurityConfig(ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter,
                          UserJwtAuthenticationFilter userJwtAuthenticationFilter) {
        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;
        this.userJwtAuthenticationFilter = userJwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(userJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 仅最小健康检查匿名可达；metrics/env 等管理端点需要网络层限制（部署边界）
                .requestMatchers("/error", "/actuator/health", "/actuator/health/**").permitAll()
                // 内部端点：仅服务令牌（ROLE_INTERNAL），控制器 @PreAuthorize 再核能力域
                .requestMatchers("/auth/**", "/logs/**").hasAuthority("ROLE_INTERNAL")
                // 管理端点：已验签管理员；细粒度权限由 AdminPermissionInterceptor 承担
                .anyRequest().hasAuthority("ROLE_ADMIN")
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint(
                    (request, response, authException) ->
                            JsonAuthenticationEntryPoint.writeUnauthorized(request, response)));
        return http.build();
    }
}
