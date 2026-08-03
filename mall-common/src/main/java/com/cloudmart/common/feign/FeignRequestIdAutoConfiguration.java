package com.cloudmart.common.feign;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(RequestInterceptor.class)
public class FeignRequestIdAutoConfiguration {

    @Bean
    public FeignRequestIdInterceptor feignRequestIdInterceptor() {
        return new FeignRequestIdInterceptor();
    }
}
