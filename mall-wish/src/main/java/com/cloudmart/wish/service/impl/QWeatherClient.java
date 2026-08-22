package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.enums.TreeWeather;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 和风天气 v7 API 客户端（Sprint 2.2，文档 28.1.2 生命树动态环境天气联动）。
 *
 * <p><b>降级策略（文档 28.1.2：API 不可用时默认晴天，不报错）</b>：
 * 未启用/未配 Key/网络异常/超时/非 200 业务码，一律返回 {@link TreeWeather#SUNNY}，
 * 天气为展示性数据，Fail-Open 不阻断环境聚合。</p>
 *
 * <p><b>缓存（文档 Sprint 2.2 验收：5 分钟缓存不重复请求）</b>：
 * Redis {@code wish:tree:weather} TTL 5 分钟（经 Nacos 可调）；Redis 异常
 * Fail-Open 降级为 miss/仅告警。开发版免费额度 1000 次/天，缓存后
 * 单实例调用量 ≤288 次/天。</p>
 *
 * <p><b>重试策略</b>：不重试——天气非关键数据且降级语义完整，
 * 盲目重试浪费免费额度（AGENTS.md 16.2：Retry 必须明确 retryable 语义）。</p>
 */
@Component
@Slf4j
public class QWeatherClient {

    /** Redis 天气缓存 Key（public 供集成测试断言/清理） */
    public static final String WEATHER_CACHE_KEY = "wish:tree:weather";

    private final RestClient restClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WishTreeEnvProperties props;

    public QWeatherClient(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          WishTreeEnvProperties props) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
        // 外部 API 必须限时（AGENTS.md 16.1）：连接 2s / 读 3s，快速失败走降级
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * 获取当前天气（全站单点天气，配置 LocationID）。
     *
     * @return 天气枚举；任何失败路径降级 SUNNY
     */
    public TreeWeather getCurrentWeather() {
        WishTreeEnvProperties.Weather weather = props.getWeather();
        if (!weather.isEnabled() || weather.getApiKey() == null || weather.getApiKey().isBlank()) {
            return TreeWeather.SUNNY;
        }
        TreeWeather cached = readCache();
        if (cached != null) {
            return cached;
        }
        TreeWeather current = fetchFromApi(weather);
        writeCache(current, weather.getCacheTtlMinutes());
        return current;
    }

    /**
     * 调用和风天气 v7 实时天气接口并映射枚举。
     *
     * <p>可见性为 package-private：单测以 spy 覆写本方法拦截真实外呼
     * （外部付费 API 禁止测试外呼），缓存与降级路径不依赖网络。</p>
     */
    TreeWeather fetchFromApi(WishTreeEnvProperties.Weather weather) {
        try {
            String response = restClient.get()
                    .uri(weather.getHost() + "/v7/weather/now?location={location}&key={key}",
                            weather.getLocation(), weather.getApiKey())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            // 和风 v7：HTTP 200 + 业务 code=200 才有效；非 200 视为失败降级
            if (root == null || !"200".equals(root.path("code").asText())) {
                log.warn("和风天气返回非 200 业务码（降级晴天）: {}",
                        root == null ? "null" : root.path("code").asText());
                return TreeWeather.SUNNY;
            }
            String text = root.path("now").path("text").asText(null);
            TreeWeather result = TreeWeather.fromQWeatherText(text);
            log.debug("和风天气拉取成功: text={}, mapped={}", text, result);
            return result;
        } catch (Exception ex) {
            log.warn("和风天气拉取失败（降级晴天）: {}", ex.getMessage());
            return TreeWeather.SUNNY;
        }
    }

    private TreeWeather readCache() {
        try {
            String cached = redisTemplate.opsForValue().get(WEATHER_CACHE_KEY);
            if (cached == null) {
                return null;
            }
            return TreeWeather.valueOf(cached);
        } catch (DataAccessException | IllegalArgumentException ex) {
            // Fail-Open：Redis 异常/脏值降级为 miss 回源
            log.warn("天气缓存读取失败（降级回源）: {}", ex.getMessage());
            return null;
        }
    }

    private void writeCache(TreeWeather weather, int ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(WEATHER_CACHE_KEY, weather.name(),
                    Duration.ofMinutes(ttlMinutes));
        } catch (DataAccessException ex) {
            // Fail-Open：写失败仅告警（下次读取回源）
            log.warn("天气缓存写入失败（仅告警）: {}", ex.getMessage());
        }
    }
}
