package com.cloudmart.file.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartConfigurationTest {
    @Test
    void uploadLimitsAreUnderSpringServletWithRoomForMultipartOverhead() throws Exception {
        var properties = new YamlPropertySourceLoader()
                .load("file", new ClassPathResource("application.yml")).getFirst();
        assertThat(properties.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("50MB");
        assertThat(properties.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("55MB");
        assertThat(properties.getProperty("mybatis-plus.servlet.multipart.max-file-size")).isNull();
    }
}
