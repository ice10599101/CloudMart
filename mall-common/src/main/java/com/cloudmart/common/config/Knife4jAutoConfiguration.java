package com.cloudmart.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class Knife4jAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI cloudmartOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CloudMart API")
                        .description("CloudMart 全栈微服务电商系统 API 文档")
                        .version("1.0.0")
                        .license(new License().name("Apache 2.0")));
    }
}
