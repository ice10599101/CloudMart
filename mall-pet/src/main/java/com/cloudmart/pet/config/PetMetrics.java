package com.cloudmart.pet.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * B22 关键指标计数器：结算未知/配额拒绝/事件失败/状态冲突。
 * 以 Registry 计数器暴露，Grafana/Prometheus 从 actuator/prometheus 抓取。
 */
@Component
public class PetMetrics {

    private final MeterRegistry registry;

    public PetMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void increment(String name, String tagKey, String tagValue) {
        Counter.builder(name).tag(tagKey, tagValue).register(registry).increment();
    }
}
