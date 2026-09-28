package com.cloudmart.auth.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.cloudmart.auth.util.JwtProvider;
import com.cloudmart.common.filter.RequestIdFilter;
import com.cloudmart.common.security.JsonAuthenticationEntryPoint;
import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * SEC-02/03：本服务的 JWT 解码器（/logout 等 @AuthenticationPrincipal Jwt 依赖它）。
     * 修复缺陷：仅有 Decoder Bean 不会自动建立 Jwt principal，必须配合
     * {@code oauth2ResourceServer} 才能让登出端点真正拿到已验签令牌。
     * 校验签发方与受众（与 JwtProvider 同源），公钥来自本服务 RSA 密钥。
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource, RSAKey rsaKey) {
        try {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(JwtProvider.ISSUER),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            audience -> audience != null && audience.contains(JwtProvider.AUDIENCE))));
            return decoder;
        } catch (JOSEException e) {
            throw new IllegalStateException("SEC03：JwtDecoder 初始化失败", e);
        }
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter,
            JwtDecoder jwtDecoder) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .logout(logout -> logout.disable())
            // SEC-02：/internal/tokens/** 供 mall-admin 经服务令牌撤销主体令牌
            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // SEC-02：登出端点必须拿到已验签令牌才能撤销会话——配置资源服务器 JWT 认证链
            .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.decoder(jwtDecoder)))
            .authorizeHttpRequests(auth -> auth
                // SEC-02：logout 保留 Bearer 并要求认证（匿名登出=伪成功，禁止）
                .requestMatchers("/logout", "/logout-all", "/admin/logout", "/admin/logout-all").authenticated()
                .requestMatchers("/login", "/refresh", "/admin/**", "/oauth2/jwks", "/.well-known/**", "/actuator/**",
                    "/doc.html", "/webjars/**", "/swagger-resources/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> JsonAuthenticationEntryPoint.writeUnauthorized(request, response)));
        return http.build();
    }
}
