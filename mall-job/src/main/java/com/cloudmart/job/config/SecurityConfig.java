package com.cloudmart.job.config;

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
 * mall-job 安全配置（SEC-01）：管理后台经网关携带管理员 JWT 直连本服务，
 * 用户/管理员 JWT 由 {@link UserJwtAuthenticationFilter} 本地验签建立身份；
 * @RequiresPermission 注解由 SecurityAuthAutoConfiguration 注册的
 * AdminPermissionInterceptor 执行（上下文来自管理员令牌声明）。
 * 服务间调用（/internal/**）须携带 job:internal 服务令牌。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserJwtAuthenticationFilter userJwtAuthenticationFilter;
    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;

    public SecurityConfig(UserJwtAuthenticationFilter userJwtAuthenticationFilter,
                          ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter) {
        this.userJwtAuthenticationFilter = userJwtAuthenticationFilter;
        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(userJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/**").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/swagger-resources/**",
                        "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) ->
                com.cloudmart.common.security.JsonAuthenticationEntryPoint.writeUnauthorized(request, response)));
        return http.build();
    }
}
