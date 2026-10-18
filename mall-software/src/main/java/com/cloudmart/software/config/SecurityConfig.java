package com.cloudmart.software.config;

import com.cloudmart.common.filter.RequestIdFilter;
import com.cloudmart.common.security.JsonAuthenticationEntryPoint;
import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
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

/**
 * 小答软件模块 Spring Security 配置。
 *
 * <p>身份边界（SEC-01）：用户/管理员/服务三种主体全部由 mall-common 公共安全组件建立。</p>
 * <ul>
 *   <li>用户/管理员：网关透传的 Bearer JWT 由公共 {@link UserJwtAuthenticationFilter}
 *       完整验签（RS256/mall-auth JWKS、iss/aud/exp/nbf、身份域 scope、会话撤销
 *       authVersion），建立 ROLE_USER / ROLE_ADMIN；认证成功后 X-User-Id 被强制
 *       改写为令牌主体，直连伪造头失效；匿名伪造由 {@link UserIdentityHeaderGuardFilter}
 *       剥离。</li>
 *   <li>服务：mall-admin 的管理代理必须携带短期签名服务令牌，由公共
 *       {@link ServiceTokenAuthenticationFilter} 按 application.yml 中
 *       {@code cloudmart.security.service-token-paths} 的路径强映射校验 iss/scope
 *       （/admin 要求 iss=mall-admin、scope=software:admin；/internal/account-erasure
 *       要求 iss=mall-user、scope=software:erasure），建立 ROLE_INTERNAL；
 *       用户令牌永远不会得到该角色。没有任何内部接口默认公开。</li>
 *   <li>匿名：仅方案 7.2 列举的公开只读端点 permitAll；固定路径登录端点
 *       （/software/mine、/software/publisher-status 等）在动态 {id} 匹配之前
 *       显式声明，禁止 GET /software/** 一把放开。</li>
 * </ul>
 *
 * <p>路由可见性策略：游客可浏览软件广场、详情、版本来源摘要、评论与分类标签；
 * resolve/收藏/评论/发布/编辑一律 authenticated（查看条件的等级/付款裁决在
 * SoftwareLinkAccessPolicy 业务层执行，不在此层）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserJwtAuthenticationFilter userJwtAuthenticationFilter;
    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;
    private final RequestIdFilter requestIdFilter;

    public SecurityConfig(UserJwtAuthenticationFilter userJwtAuthenticationFilter,
                          ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter,
                          RequestIdFilter requestIdFilter) {
        this.userJwtAuthenticationFilter = userJwtAuthenticationFilter;
        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;
        this.requestIdFilter = requestIdFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 顺序：先验用户 JWT，再验服务令牌（二者互斥建立身份）；最后跑用户域身份头护栏
            .addFilterBefore(userJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new UserIdentityHeaderGuardFilter(), ServiceTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // 登录端点：固定路径必须先于 /software/{id} 动态匹配（7.3；不得落入匿名通配）
                .requestMatchers(HttpMethod.GET, "/software/mine", "/software/publisher-status",
                        "/software/preferences", "/software/access-contacts",
                        "/software/level-options",
                        "/software/publisher-applications/mine").authenticated()
                // SF03：发布者申请（登录必达；固定路径先于动态 {id}）
                .requestMatchers(HttpMethod.POST, "/software/publisher-applications",
                        "/software/publisher-applications/{applicationId}/cancel")
                .authenticated()
                .requestMatchers(HttpMethod.GET, "/software/{id}/access-state",
                        "/software/{id}/editor").authenticated()
                // 公开浏览：软件广场/分类/标签/榜单/专题（7.2，可见性由业务层过滤）
                .requestMatchers(HttpMethod.GET, "/software", "/software/categories",
                        "/software/tags", "/software/rankings", "/software/topics",
                        "/software/topics/{slug}").permitAll()
                // 公开浏览：详情、查看条件摘要、版本来源摘要（无 URL/提取码）
                .requestMatchers(HttpMethod.GET, "/software/{id}", "/software/{id}/access-policy",
                        "/software/{id}/releases").permitAll()
                // 公开浏览：评论根楼与回复分页（宿主可见性由业务层过滤）
                .requestMatchers(HttpMethod.GET, "/software/{id}/comments",
                        "/software/{id}/comments/*/replies").permitAll()
                // 服务域：管理代理与内部擦除端点（纵深防御；@PreAuthorize 保留）
                .requestMatchers("/admin/**").hasRole("INTERNAL")
                .requestMatchers("/internal/**").hasRole("INTERNAL")
                // 健康探针匿名；prometheus 端点匿名（指标无 PII，供抓取；
                // 其余 actuator 仍不匿名可达）
                .requestMatchers("/error", "/actuator/health", "/actuator/prometheus").permitAll()
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
