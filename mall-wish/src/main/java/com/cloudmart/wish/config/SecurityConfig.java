package com.cloudmart.wish.config;

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
 * 心愿宇宙模块 Spring Security 配置。
 *
 * <p>身份边界（T02）：用户/管理员/服务三种主体全部由公共安全组件建立，
 * 心愿模块不再持有本地 JWT 验签实现。</p>
 * <ul>
 *   <li>用户/管理员：网关透传的 Bearer JWT 由公共 {@link UserJwtAuthenticationFilter}
 *       完整验签（RS256/mall-auth JWKS、iss/aud/exp/nbf、身份域 scope、会话撤销
 *       authVersion），建立 ROLE_USER / ROLE_ADMIN；认证成功后 X-User-Id 被强制
 *       改写为令牌主体，直连伪造头失效。</li>
 *   <li>用户域护栏：{@link UserIdentityHeaderGuardFilter} 对未建立 JWT 身份的
 *       用户域请求剥离 X-User-Id——匿名伪造头在 permitAll 公开端点同样失效。</li>
 *   <li>服务：mall-admin/mall-job/mall-pet/mall-user 的内部调用必须携带短期签名
 *       服务令牌，由公共 {@link ServiceTokenAuthenticationFilter} 按 application.yml
 *       中 {@code cloudmart.security.service-token-paths} 的路径强映射校验
 *       iss/scope，建立 ROLE_INTERNAL；用户令牌永远不会得到该角色。</li>
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

    // T02 修复：Clock bean 不在 SecurityConfig 内定义——公共自动配置
    // （cloudmartSecurityClock，@ConditionalOnMissingBean）向 userJwtAuthenticationFilter
    // 提供时钟；Clock 定义在本类会形成 securityConfig → userJwtAuthenticationFilter →
    // Clock → securityConfig 循环依赖，应用无法启动。

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(requestIdFilter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
