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

    /** 累计型计数（P1-8：token 消耗/延迟等按量累加） */
    public void add(String name, double amount) {
        Counter.builder(name).register(registry).increment(amount);
    }

    public void add(String name, double amount, String tagKey, String tagValue) {
        Counter.builder(name).tag(tagKey, tagValue).register(registry).increment(amount);
    }

    /** 读取当前累计值（看板展示用；指标不存在返回 0） */
    public double value(String name) {
        Counter counter = registry.find(name).counter();
        return counter != null ? counter.count() : 0;
    }
}
