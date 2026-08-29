package com.cloudmart.wish.config;

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
 * 心愿宇宙模块 Spring Security 配置。
 *
 * <p>路由可见性策略（对应文档 2.1 节）：</p>
 * <ul>
 *   <li>GET /wishes（公开列表）、GET /wishes/{id}（详情）、GET /categories（字典）、
 *       GET /home（首页聚合）→ permitAll（未登录可浏览，登录后个性化）</li>
 *   <li>POST/PUT/DELETE /wishes/**、GET /my/**、/admin/** → authenticated</li>
 *   <li>/admin/** 由 Controller 层 @PreAuthorize("hasRole('INTERNAL')") 限制为内部调用
 *       （mall-admin Feign 代理），外部经网关的普通用户请求无法到达</li>
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
                // 公开浏览：心愿列表、详情、分类字典、首页聚合
                .requestMatchers(HttpMethod.GET, "/wishes").permitAll()
                .requestMatchers(HttpMethod.GET, "/wishes/{id}").permitAll()
                // 公开浏览：公开心愿的还愿故事（文档 2.4 GET，与心愿详情同语义）
                .requestMatchers(HttpMethod.GET, "/wishes/{id}/fulfillment").permitAll()
                .requestMatchers(HttpMethod.GET, "/categories").permitAll()
                .requestMatchers(HttpMethod.GET, "/home").permitAll()
                // 公开浏览：世界生命树环境状态 + 环境渲染配置（未登录首页/世界树亦需渲染）
                .requestMatchers(HttpMethod.GET, "/tree-env", "/tree-env/configs").permitAll()
                // 公开浏览：世界生命树 3D 聚合状态 + 果实视口分页（Sprint 2.1）
                .requestMatchers(HttpMethod.GET, "/tree").permitAll()
                .requestMatchers(HttpMethod.GET, "/tree/fruits").permitAll()
                // 公开浏览：徽章图鉴（未登录可浏览，文档 2.9）
                .requestMatchers(HttpMethod.GET, "/badges/definitions").permitAll()
                // 公开播放：背景音乐播放列表（未登录页面亦需 BGM，Sprint 2.3）
                .requestMatchers(HttpMethod.GET, "/bgm/playlist").permitAll()

                // 排行榜公开浏览（Sprint 2.7）
                .requestMatchers(HttpMethod.GET, "/leaderboard").permitAll()

                // 灰度功能开关（Sprint 2.8，四端降级开关数据源；匿名仅全量放行）
                .requestMatchers(HttpMethod.GET, "/feature-flags").permitAll()

                // LBS 地图公开浏览（Sprint 3.1，仅返回 PUBLIC 心愿模糊化坐标）
                .requestMatchers(HttpMethod.GET, "/map/wishes", "/map/cluster").permitAll()

                // 温暖事件公开浏览（Sprint 3.2，仅可见状态事件）
                .requestMatchers(HttpMethod.GET, "/map/warm-events", "/map/warm-events/cluster").permitAll()

                // 直播挂件公开数据（Sprint 3.4，CDN 友好，10s 缓存）
                .requestMatchers(HttpMethod.GET, "/live/widget/*").permitAll()

                // 社区活动公开浏览（Sprint 3.5；参与/申请/看板需登录）
                .requestMatchers(HttpMethod.GET, "/activities", "/activities/{id}",
                        "/activities/{id}/progress").permitAll()

                // 同愿匹配推荐公开浏览（Sprint 2.6；匿名降级为纯参数匹配）
                .requestMatchers(HttpMethod.GET, "/match/groups/recommend").permitAll()
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
