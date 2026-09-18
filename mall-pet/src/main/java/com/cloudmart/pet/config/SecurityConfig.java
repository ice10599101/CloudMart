package com.cloudmart.pet.config;

import com.cloudmart.common.filter.RequestIdFilter;
import com.cloudmart.common.security.JsonAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 社区宠物模块 Spring Security 配置。
 *
 * <p>路由可见性策略：</p>
 * <ul>
 *   <li>GET /public/{userId}（他人主页宠物卡片）→ permitAll（未登录可浏览）</li>
 *   <li>其余用户侧接口（互动/打工/读书/捞瓶/对战/聊天/成就）→ authenticated</li>
 *   <li>/internal/** 由 Controller 层 @PreAuthorize("hasRole('INTERNAL')") 限制为内部调用，
 *       外部经网关的普通用户请求无法到达</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final InternalCallAuthenticationFilter internalCallAuthenticationFilter;
    private final RequestIdFilter requestIdFilter;

    public SecurityConfig(InternalCallAuthenticationFilter internalCallAuthenticationFilter,
                          RequestIdFilter requestIdFilter) {
        this.internalCallAuthenticationFilter = internalCallAuthenticationFilter;
        this.requestIdFilter = requestIdFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(internalCallAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 公开浏览：他人主页宠物卡片（隐私开关由宠物主人控制，关闭时仅返回 404 语义提示）
                .requestMatchers(HttpMethod.GET, "/public/*").permitAll()
                // 文档与监控端点
                .requestMatchers("/error", "/actuator/**").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/swagger-resources/**",
                                  "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) ->
                JsonAuthenticationEntryPoint.writeUnauthorized(request, response)));
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
