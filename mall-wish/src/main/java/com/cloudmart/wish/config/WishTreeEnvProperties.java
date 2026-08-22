package com.cloudmart.wish.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 生命树情绪环境联动配置（文档 2.2 气象情绪联动，前缀 {@code wish.tree-env}）。
 *
 * <p>通过 Nacos（mall-wish.yml）热更新：阈值与窗口调整无需重启服务。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wish.tree-env")
public class WishTreeEnvProperties {

    /** 情绪聚合滑动窗口（分钟，文档 2.2：1 小时） */
    private int moodWindowMinutes = 60;

    /**
     * 时间衰减系数 λ：权重 w = exp(-λ × 样本年龄分钟数)。
     * 默认 0.0231（= ln4/60，1 小时前样本权重衰减至 25%，越新权重越高）。
     */
    private double moodDecayLambda = 0.0231;

    /** 下雨阈值：mood 低于该值触发 RAIN（文档 2.2：-0.6） */
    private double rainThreshold = -0.6;

    /** 彩虹阈值：mood 高于该值触发 RAINBOW（文档 2.2：+0.3） */
    private double rainbowThreshold = 0.3;

    /** 下雨最短持续（分钟，文档 2.2：最短 30 分钟） */
    private int rainMinDurationMinutes = 30;

    /** 彩虹持续（分钟，文档 2.2：15 分钟） */
    private int rainbowDurationMinutes = 15;

    /** BLESS 突增检测窗口（分钟；当前窗口 vs 前一窗口对比） */
    private int blessBurstWindowMinutes = 15;

    /** BLESS 突增最小计数：当前窗口低于该值不视为突增（防低基数误报） */
    private int blessBurstMinCount = 5;

    /** BLESS 突增倍率：当前窗口 ≥ 前一窗口 × 该倍率视为突增 */
    private double blessBurstMultiplier = 2.0;

    /** Redis mood 聚合分数缓存 TTL（分钟，文档 2.2：10 分钟） */
    private int moodCacheTtlMinutes = 10;

    /** 扫描互斥锁 TTL（秒；略大于单次扫描预期耗时，防多实例并发扫描） */
    private int scanLockTtlSeconds = 240;

    /** 天气 API 子配置（Sprint 2.2 和风天气 v7，文档 28.1.2） */
    private Weather weather = new Weather();

    /**
     * 和风天气 API 配置（文档 28.1.2：开发版免费 1000 次/天，满足
     * 生命树天气更新频率；缓存 5 分钟后实际调用量 ≤288 次/天单实例）。
     */
    @Getter
    @Setter
    public static class Weather {

        /** 是否启用真实天气拉取（false/未配 Key 时恒返回 SUNNY 降级值） */
        private boolean enabled = false;

        /** 和风天气 API Key（敏感配置走环境变量，禁止硬编码） */
        private String apiKey = "";

        /** 天气查询位置（和风 LocationID；默认 101010100 北京，全站单点天气） */
        private String location = "101010100";

        /** API Host（开发版 devapi；付费商业版换 api.qweather.com） */
        private String host = "https://devapi.qweather.com";

        /** Redis 天气缓存 TTL（分钟，文档 Sprint 2.2 验收：5 分钟不重复请求） */
        private int cacheTtlMinutes = 5;
    }
}
