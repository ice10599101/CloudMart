package com.cloudmart.software.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 软件模块 MVC 配置：注册总开关拦截器（仅覆盖 /software 公开面） */
@Configuration
@RequiredArgsConstructor
public class SoftwareWebMvcConfiguration implements WebMvcConfigurer {

    private final SoftwareAvailabilityInterceptor softwareAvailabilityInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(softwareAvailabilityInterceptor)
                .addPathPatterns("/software", "/software/**");
    }
}
