package com.cloudmart.gen.config;

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
 * mall-gen 安全配置（SEC-01）：代码生成属开发工具，全部端点要求已认证身份——
 * 管理后台经网关携带管理员 JWT，本地验签后由 @RequiresPermission 注解
 * （SecurityAuthAutoConfiguration 注册的拦截器执行）做细粒度控制。
 * 旧网关配置中 /api/gen/preview、/api/gen/download 的公开放行已同步移除。
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
