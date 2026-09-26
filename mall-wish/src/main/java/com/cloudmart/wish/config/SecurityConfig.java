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

import java.time.Clock;

/**
 * 心愿宇宙模块 Spring Security 配置。
 *
 * <p>身份边界（B01）：双轨认证，不再存在"头即身份"。</p>
 * <ul>
 *   <li>用户：网关透传的 Bearer JWT 由 {@link WishJwtAuthenticationFilter} 直接验签
 *       （RS256/mall-auth JWKS），建立 ROLE_USER；网关注入的 {@code X-User-Id}
 *       仅作为数据字段被 Controller 读取，不再作为身份源。</li>
 *   <li>服务：mall-admin/mall-job/mall-pet 的内部调用必须携带短期签名服务令牌，
 *       由 {@link ServiceTokenAuthenticationFilter} 按路径强校验 iss/aud/scope，
 *       建立 ROLE_INTERNAL；用户令牌永远不会得到该角色。</li>
 *   <li>匿名：公开端点（下方 permitAll）不依赖任何身份即可浏览。</li>
 * </ul>
 *
 * <p>路由可见性策略：GET /wishes、/wishes/{id}、/categories、/home 等公开浏览端点
 * permitAll；写操作、/my/**、/admin/**、/internal/** 一律 authenticated
 * （/admin 与 /internal 再由 @PreAuthorize("hasRole('INTERNAL')") 限定服务身份）。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final WishSecurityProperties securityProperties;
    private final RequestIdFilter requestIdFilter;

    public SecurityConfig(WishSecurityProperties securityProperties,
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
            .addFilterBefore(new WishJwtAuthenticationFilter(securityProperties.jwksUri()),
                    UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new ServiceTokenAuthenticationFilter(securityProperties, wishClock()),
                    UsernamePasswordAuthenticationFilter.class)
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

                // 地图前端配置下发（高德 Key/安全密钥，浏览器端渲染用；匿名地图页同需）
                .requestMatchers(HttpMethod.GET, "/map/config").permitAll()

                // 直播挂件公开数据（Sprint 3.4，CDN 友好，10s 缓存）
                .requestMatchers(HttpMethod.GET, "/live/widget/*").permitAll()

                // 社区活动公开浏览（Sprint 3.5；参与/申请/看板需登录）
                .requestMatchers(HttpMethod.GET, "/activities", "/activities/{id}",
                        "/activities/{id}/progress").permitAll()

                // 虚拟工坊/品牌公开浏览（Sprint 3.6；兑换/收藏馆/切换需登录）
                .requestMatchers(HttpMethod.GET, "/workshop/assets", "/brands",
                        "/brands/*/pools").permitAll()

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

    /** 供时间相关校验注入的 UTC 时钟（后续 B09 等任务统一复用）。 */
    @Bean
    public Clock wishClock() {
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
