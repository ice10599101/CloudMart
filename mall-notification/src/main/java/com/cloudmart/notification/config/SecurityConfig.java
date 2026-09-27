package com.cloudmart.notification.config;

import com.cloudmart.common.filter.RequestIdFilter;
import com.cloudmart.common.security.JsonAuthenticationEntryPoint;
import com.cloudmart.common.security.ServiceTokenAuthenticationFilter;
import com.cloudmart.common.security.UserJwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
            .addFilterBefore(userJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(serviceTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/actuator/**", "/doc.html", "/webjars/**",
                        "/swagger-resources/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(eh -> eh.authenticationEntryPoint((request, response, authException) -> JsonAuthenticationEntryPoint.writeUnauthorized(request, response)));
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
