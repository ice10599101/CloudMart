package com.cloudmart.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.module.SimpleModule;

import java.text.SimpleDateFormat;

@AutoConfiguration
public class CommonAutoConfiguration {

    private static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            SimpleModule jsSafeLongModule = new SimpleModule("jsSafeLong");
            jsSafeLongModule.addSerializer(Long.class, new JsSafeLongSerializer());
            jsSafeLongModule.addSerializer(Long.TYPE, new JsSafeLongSerializer());
            builder.defaultDateFormat(new SimpleDateFormat(DATETIME_FORMAT))
                    .addModule(jsSafeLongModule);
        };
    }
}
