package com.cloudmart.pet.config;

import com.cloudmart.common.filter.RequestIdFilter;
import com.cloudmart.common.security.CloudmartSecurityProperties;
import com.cloudmart.common.security.JsonAuthenticationEntryPoint;
import com.cloudmart.common.security.RedisAuthRevocationChecker;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
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
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/**
 * 社区宠物模块 Spring Security 配置。
 *
 * <p>身份边界（SEC-01/R08）：双轨认证，不再存在"头即身份"。</p>
 * <ul>
 *   <li>用户：网关透传的 Bearer JWT 由 mall-common {@link UserJwtAuthenticationFilter}
 *       本地验签——RS256/kid 匹配 JWKS、exp 必填、nbf/iss/aud/sid/authVersion/scope
 *       与 subjectType 全量校验、撤销状态 Redis 检查（fail-closed）；建立 ROLE_USER，
 *       并把 {@code X-User-Id} 头权威化为已验证 subject。对齐网关与 mall-common 同一
 *       验证器（此前宠物自有过滤器缺 exp 必填与声明校验，直连场景弱于全局标准）。</li>
 *   <li>服务：mall-admin 的管理代理必须携带短期签名服务令牌，由
 *       {@link PetServiceTokenAuthenticationFilter} 按路径强校验 iss/aud/scope
 *       （/admin/** 要求 iss=mall-admin、scope=pet:admin），建立 ROLE_INTERNAL；
 *       用户令牌永远不会得到该角色。</li>
 *   <li>匿名：公开端点（下方 permitAll）不依赖任何身份即可浏览；监控仅最小健康探针匿名。</li>
 * </ul>
 *
 * <p>路由可见性策略：GET /public/{userId}（他人主页宠物卡片）→ permitAll（未登录可浏览）；
 * /admin/** 在过滤链显式限定 INTERNAL（纵深防御，Controller @PreAuthorize 保留）；
 * 其余用户侧接口（互动/打工/读书/捞瓶/对战/聊天/成就）→ authenticated。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final PetSecurityProperties securityProperties;
    private final Clock clock;
    private final RequestIdFilter requestIdFilter;
    private final StringRedisTemplate redisTemplate;

    public SecurityConfig(PetSecurityProperties securityProperties,
                          RequestIdFilter requestIdFilter,
                          StringRedisTemplate redisTemplate,
                          Clock clock) {
        this.securityProperties = securityProperties;
        this.clock = clock;
        this.requestIdFilter = requestIdFilter;
        this.redisTemplate = redisTemplate;
    }

    /** R08：以 mall-common 统一验证器替换宠物自有 JWT 过滤器（声明校验/撤销检查对齐全局标准） */
    private UserJwtAuthenticationFilter userJwtAuthenticationFilter() {
        CloudmartSecurityProperties commonProps = new CloudmartSecurityProperties();
        commonProps.setJwksUri(securityProperties.getJwksUri());
        commonProps.setClockSkewSeconds(securityProperties.getClockSkewSeconds());
        // 签发方/受众使用 mall-common 默认值（cloudmart-auth/cloudmart-api），与网关与 mall-file 同源；
        // 撤销检查 fail-closed（Redis 故障时拒绝建立身份，绝不放行未校验会话）
        return new UserJwtAuthenticationFilter(commonProps, clock, new RedisAuthRevocationChecker(redisTemplate));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 顺序：先验用户 JWT，再验服务令牌（二者互斥建立身份）
            .addFilterBefore(userJwtAuthenticationFilter(),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new PetServiceTokenAuthenticationFilter(securityProperties, clock),
                    UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 公开浏览：他人主页宠物卡片（隐私开关由宠物主人控制，关闭时仅返回 404 语义提示）
                .requestMatchers(HttpMethod.GET, "/public/*").permitAll()
                // 健康探针匿名（仅最小端点）；其余 actuator（metrics/env 等）不再匿名可达（R08）
                .requestMatchers("/error", "/actuator/health").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/swagger-resources/**",
                                  "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // /admin/** 过滤链显式限定服务身份（纵深防御；@PreAuthorize 保留）
                .requestMatchers("/admin/**").hasRole("INTERNAL")
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
