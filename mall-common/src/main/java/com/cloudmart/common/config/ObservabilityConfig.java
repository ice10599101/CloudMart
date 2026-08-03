package com.cloudmart.common.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 通用可观测性配置。
 * 为所有微服务统一注入应用名称标签，便于 Prometheus 按 service 维度聚合。
 */
@Configuration
@ConditionalOnClass(MeterRegistry.class)
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonMetricsCustomizer() {
        return registry -> registry.config()
                .commonTags("application", "${spring.application.name:unknown}");
    }
}
