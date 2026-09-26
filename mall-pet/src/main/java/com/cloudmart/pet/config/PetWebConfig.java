package com.cloudmart.pet.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 装配：注册请求上下文拦截器（Idempotency-Key 捕获）。
 */
@Configuration
@RequiredArgsConstructor
public class PetWebConfig implements WebMvcConfigurer {

    private final PetRequestContext petRequestContext;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(petRequestContext).addPathPatterns("/**");
    }
}
