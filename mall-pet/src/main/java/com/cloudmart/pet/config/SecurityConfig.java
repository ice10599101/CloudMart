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

import java.time.Clock;

/**
 * 社区宠物模块 Spring Security 配置。
 *
 * <p>身份边界（SEC-01）：双轨认证，不再存在"头即身份"。</p>
 * <ul>
 *   <li>用户：网关透传的 Bearer JWT 由 {@link PetJwtAuthenticationFilter} 直接验签
 *       （RS256/mall-auth JWKS），建立 ROLE_USER，并把 {@code X-User-Id} 头权威化为
 *       已验证 subject；网关注入的头仅作为透传值，不再作为身份源。</li>
 *   <li>服务：mall-admin 的管理代理必须携带短期签名服务令牌，由
 *       {@link PetServiceTokenAuthenticationFilter} 按路径强校验 iss/aud/scope
 *       （/admin/** 要求 iss=mall-admin、scope=pet:admin），建立 ROLE_INTERNAL；
 *       用户令牌永远不会得到该角色。</li>
 *   <li>匿名：公开端点（下方 permitAll）不依赖任何身份即可浏览。</li>
 * </ul>
 *
 * <p>路由可见性策略：GET /public/{userId}（他人主页宠物卡片）→ permitAll（未登录可浏览）；
 * 其余用户侧接口（互动/打工/读书/捞瓶/对战/聊天/成就）→ authenticated；
 * /admin/** 再由 Controller 层 @PreAuthorize("hasRole('INTERNAL')") 限定服务身份。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final PetSecurityProperties securityProperties;
    private final RequestIdFilter requestIdFilter;

    public SecurityConfig(PetSecurityProperties securityProperties,
                          RequestIdFilter requestIdFilter) {
        this.securityProperties = securityProperties;
        this.requestIdFilter = requestIdFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 顺序：先验用户 JWT，再验服务令牌（二者互斥建立身份）
            .addFilterBefore(new PetJwtAuthenticationFilter(securityProperties.getJwksUri()),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new PetServiceTokenAuthenticationFilter(securityProperties, petClock()),
                    UsernamePasswordAuthenticationFilter.class)
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

    /** 供服务令牌时效校验注入的 UTC 时钟。 */
    @Bean
    public Clock petClock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
