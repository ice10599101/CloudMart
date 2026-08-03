package com.cloudmart.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.text.SimpleDateFormat;

@AutoConfiguration
public class CommonAutoConfiguration {

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> builder.defaultDateFormat(new SimpleDateFormat(DATETIME_FORMAT));
    }
}
