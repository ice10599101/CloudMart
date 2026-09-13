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
 * 全站天气 Redis {@code wish:tree:weather}；坐标天气（用户定位个性化）
 * 键 {@code wish:tree:weather:{lat},{lng}}（2 位小数≈1km 网格，防止键膨胀），
 * TTL 均为 5 分钟（经 Nacos 可调）；Redis 异常 Fail-Open 降级为 miss/仅告警。
 * 开发版免费额度 1000 次/天，缓存后单实例调用量可控。</p>
 *
 * <p><b>重试策略</b>：不重试——天气非关键数据且降级语义完整，
 * 盲目重试浪费免费额度（AGENTS.md 16.2：Retry 必须明确 retryable 语义）。</p>
 */
@Component
@Slf4j
public class QWeatherClient {

    /** Redis 天气缓存 Key 前缀（public 供集成测试断言/清理） */
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
     * 获取当前天气（全站单点天气，配置 LocationID，默认北京）。
     *
     * @return 天气枚举；任何失败路径降级 SUNNY
     */
    public TreeWeather getCurrentWeather() {
        WishTreeEnvProperties.Weather weather = props.getWeather();
        TreeWeather fetched = fetchCurrentWeather(weather, weather.getLocation(), WEATHER_CACHE_KEY);
        return fetched != null ? fetched : TreeWeather.SUNNY;
    }

    /**
     * 获取指定坐标的当前天气（用户定位个性化，BUG#46 需求：天气优先按用户定位）。
     *
     * <p>和风 v7 {@code location} 直接支持「经度,纬度」坐标（保留 2 位小数，
     * 约 1km 网格，缓存键按坐标粒度隔离）。任何失败（未启用/无 Key/网络/业务码）
     * 返回 {@code fallback}——调用方传全站天气（北京），与「定位失败回退北京」
     * 的产品语义一致。</p>
     *
     * @param lng      经度
     * @param lat      纬度
     * @param fallback 失败兜底（通常传全站天气）
     * @return 坐标处天气枚举；失败返回 fallback
     */
    public TreeWeather getCurrentWeather(double lng, double lat, TreeWeather fallback) {
        WishTreeEnvProperties.Weather weather = props.getWeather();
        String location = coordsToLocation(lng, lat);
        TreeWeather fetched = fetchCurrentWeather(weather, location, cacheKey(location));
        return fetched != null ? fetched : fallback;
    }

    /** 缓存键：按 2 位小数坐标粒度（约 1km 网格）隔离，防止缓存键无限膨胀 */
    private String cacheKey(String location) {
        return WEATHER_CACHE_KEY + ":" + location;
    }

    /** 和风 location 坐标格式：「经度,纬度」（保留 2 位小数） */
    private String coordsToLocation(double lng, double lat) {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f", lng, lat);
    }

    /**
     * 拉取当前天气（严格版）：启用且成功返回天气枚举；任何失败返回 null，
     * 由调用方决定兜底（全站路径兜底 SUNNY，坐标路径兜底调用方传入值）。
     */
    private TreeWeather fetchCurrentWeather(WishTreeEnvProperties.Weather weather,
                                            String location, String cacheKey) {
        if (!weather.isEnabled() || weather.getApiKey() == null || weather.getApiKey().isBlank()) {
            return null;
        }
        TreeWeather cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }
        TreeWeather fetched = fetchFromApi(weather, location);
        if (fetched != null) {
            writeCache(cacheKey, fetched, weather.getCacheTtlMinutes());
        }
        return fetched;
    }

    /**
     * 调用和风天气 v7 实时天气接口并映射枚举（失败降级晴天）。
     *
     * <p>可见性为 package-private：单测以 spy 覆写本方法拦截真实外呼
     * （外部付费 API 禁止测试外呼），缓存与降级路径不依赖网络。</p>
     */
    TreeWeather fetchFromApi(WishTreeEnvProperties.Weather weather) {
        TreeWeather fetched = fetchFromApi(weather, weather.getLocation());
        return fetched != null ? fetched : TreeWeather.SUNNY;
    }

    /** 严格版外呼：成功返回天气，失败返回 null（不兜底） */
    TreeWeather fetchFromApi(WishTreeEnvProperties.Weather weather, String location) {
        try {
            String response = restClient.get()
                    .uri(weather.getHost() + "/v7/weather/now?location={location}&key={key}",
                            location, weather.getApiKey())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            // 和风 v7：HTTP 200 + 业务 code=200 才有效；非 200 视为失败
            if (root == null || !"200".equals(root.path("code").asText())) {
                log.warn("和风天气返回非 200 业务码: location={}, code={}",
                        location, root == null ? "null" : root.path("code").asText());
                return null;
            }
            String text = root.path("now").path("text").asText(null);
            TreeWeather result = TreeWeather.fromQWeatherText(text);
            log.debug("和风天气拉取成功: location={}, text={}, mapped={}", location, text, result);
            return result;
        } catch (Exception ex) {
            log.warn("和风天气拉取失败: location={}, {}", location, ex.getMessage());
            return null;
        }
    }

    private TreeWeather readCache(String cacheKey) {
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
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

    private void writeCache(String cacheKey, TreeWeather weather, int ttlMinutes) {
        try {
            redisTemplate.opsForValue().set(cacheKey, weather.name(),
                    Duration.ofMinutes(ttlMinutes));
        } catch (DataAccessException ex) {
            // Fail-Open：写失败仅告警（下次读取回源）
            log.warn("天气缓存写入失败（仅告警）: {}", ex.getMessage());
        }
    }
}
