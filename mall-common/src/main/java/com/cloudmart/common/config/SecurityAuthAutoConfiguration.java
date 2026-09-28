package com.cloudmart.common.config;

import com.cloudmart.common.interceptor.AdminPermissionInterceptor;
import com.cloudmart.common.security.AuthRevocationChecker;
import com.cloudmart.common.security.CloudmartSecurityProperties;
import com.cloudmart.common.security.RedisAuthRevocationChecker;
import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
import com.cloudmart.common.security.ServiceTokenProvider;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * 统一身份边界自动装配（SEC-01）：向依赖 mall-common 的服务提供
 * <ul>
 *   <li>{@link UserJwtAuthenticationFilter}：用户/管理员 JWT 本地验签；</li>
 *   <li>{@link ServiceTokenAuthenticationFilter}：入站服务令牌校验；</li>
 *   <li>{@link ServiceTokenProvider}：RestClient 等非 Feign 调用方的手工签名；</li>
 *   <li>{@link RedisAuthRevocationChecker}：直连服务撤销状态检查（默认装配）；</li>
 *   <li>{@link AdminPermissionInterceptor} 注册：使 @RequiresPermission /
 *       @RequiresAdmin 注解在 mall-job/mall-gen 等模块真正生效。</li>
 * </ul>
 *
 * <p>装配条件由 {@code cloudmart.security.*} 驱动：配置了 {@code service-id}
 * 才装配入站过滤器；mall-admin 自有管理员上下文与拦截器注册（且无需入站
 * JWT 验签），因此不配置 service-id，仅使用出站签名拦截器。</p>
 *
 * <p>afterName 指向 Spring Boot 4 的
 * {@code org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration}：
 * 保证 {@code @ConditionalOnBean(StringRedisTemplate)} 在 Redis 装配之后评估。
 * 用字符串引用避免 mall-common 对 redis autoconfigure 类名的编译依赖。</p>
 *
 * <p>注意：本类不得出现任何引用 openfeign 类型的 Bean 方法——没有 feign 依赖的
 * 模块（如 mall-product/mall-file/mall-gen）在 Spring 反射推断
 * {@code @ConditionalOnMissingBean} 返回类型时会触发
 * {@code NoClassDefFoundError: feign/RequestInterceptor}。Feign 相关 Bean 全部
 * 收敛在 {@link FeignBeanConfiguration}（类级 @ConditionalOnClass 守护）。</p>
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnClass(HandlerInterceptor.class)
@EnableConfigurationProperties(CloudmartSecurityProperties.class)
public class SecurityAuthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock cloudmartSecurityClock() {
        return Clock.systemUTC();
    }

    /** RestClient 等非 Feign 调用方的手工签名组件（不依赖 openfeign 类型） */
    @Bean
    @ConditionalOnMissingBean
    public ServiceTokenProvider serviceTokenProvider(
            CloudmartSecurityProperties properties, Clock clock) {
        return new ServiceTokenProvider(properties, clock);
    }

    @Bean
    @ConditionalOnProperty(name = "cloudmart.security.service-id")
    public UserJwtAuthenticationFilter userJwtAuthenticationFilter(
            CloudmartSecurityProperties properties, Clock clock,
            org.springframework.beans.factory.ObjectProvider<AuthRevocationChecker> revocationChecker) {
        return new UserJwtAuthenticationFilter(properties, clock, revocationChecker.getIfAvailable());
    }

    /**
     * 直连服务撤销状态检查（SEC-01）：mall-auth 的会话账本与主体版本键共享同一
     * Redis，服务本地验签通过后再查撤销状态，登出/禁用/改密对直连请求同样秒级
     * 生效；Redis 故障由检查器抛 {@link com.cloudmart.common.security.AuthStateException}
     * fail-closed 拒绝。未配置 Redis 连接工厂的模块自然跳过（仅本地验签）。
     */
    @Bean
    @ConditionalOnMissingBean(AuthRevocationChecker.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    public RedisAuthRevocationChecker redisAuthRevocationChecker(StringRedisTemplate redisTemplate) {
        return new RedisAuthRevocationChecker(redisTemplate);
    }

    @Bean
    @ConditionalOnProperty(name = "cloudmart.security.service-id")
    public ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter(
            CloudmartSecurityProperties properties, Clock clock) {
        return new ServiceTokenAuthenticationFilter(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public AdminPermissionInterceptor adminPermissionInterceptor() {
        return new AdminPermissionInterceptor();
    }

    /**
     * 注册权限拦截器使 @RequiresPermission 生效；mall-admin 通过
     * {@code register-admin-permission-interceptor=false} 关闭后自行注册
     * （保留 /auth/** 等排除路径）。
     */
    @Bean
    @ConditionalOnProperty(name = "cloudmart.security.service-id")
    public WebMvcConfigurer cloudmartAdminPermissionInterceptorRegistrar(
            CloudmartSecurityProperties properties, AdminPermissionInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                if (!properties.isRegisterAdminPermissionInterceptor()) {
                    return;
                }
                registry.addInterceptor(interceptor).addPathPatterns("/**");
            }
        };
    }

    /**
     * Feign 相关 Bean 单独守护（ASYNC/SEC-01 出站签名）：仅有 openfeign 依赖的
     * 模块才处理本配置类，避免无 feign 模块类加载失败。
     */
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignBeanConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public com.cloudmart.common.security.ServiceTokenFeignInterceptor serviceTokenFeignInterceptor(
                CloudmartSecurityProperties properties, Clock clock) {
            return new com.cloudmart.common.security.ServiceTokenFeignInterceptor(properties, clock);
        }
    }
}
