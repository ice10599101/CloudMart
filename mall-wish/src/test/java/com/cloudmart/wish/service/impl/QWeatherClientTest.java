package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.enums.TreeWeather;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * QWeatherClient 单元测试（缓存命中/回源/降级路径）。
 *
 * <p>{@code fetchFromApi} 以 spy 覆写拦截真实外呼（外部付费 API 禁止
 * 测试外呼）；和风文本映射契约见下方参数化用例。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QWeatherClient 单元测试")
class QWeatherClientTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private WishTreeEnvProperties props;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        props = new WishTreeEnvProperties();
        props.getWeather().setEnabled(true);
        props.getWeather().setApiKey("test-api-key");
    }

    /** spy 客户端：fetchFromApi 固定返回指定天气（拦截外呼） */
    private QWeatherClient newClientWithApiResult(TreeWeather fetchResult) {
        QWeatherClient client = spy(new QWeatherClient(redisTemplate, new ObjectMapper(), props));
        doReturn(fetchResult).when(client).fetchFromApi(any());
        return client;
    }

    @Nested
    @DisplayName("启用开关与降级")
    class EnabledAndFallbackTests {

        @Test
        @DisplayName("未启用：恒返回 SUNNY，不触碰 Redis 与 API")
        void disabled_returnsSunnyWithoutSideEffects() {
            props.getWeather().setEnabled(false);

            TreeWeather weather = newClientWithApiResult(TreeWeather.RAIN).getCurrentWeather();

            assertThat(weather).isEqualTo(TreeWeather.SUNNY);
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("未配置 API Key：恒返回 SUNNY（密钥缺失安全降级）")
        void blankApiKey_returnsSunny() {
            props.getWeather().setApiKey("  ");

            assertThat(newClientWithApiResult(TreeWeather.RAIN).getCurrentWeather())
                    .isEqualTo(TreeWeather.SUNNY);
        }
    }

    @Nested
    @DisplayName("Redis 缓存")
    class CacheTests {

        @Test
        @DisplayName("缓存命中：直接返回缓存值，不触发回源（5 分钟内不重复请求 API）")
        void cacheHit_returnsCachedValueWithoutFetch() {
            when(valueOperations.get(QWeatherClient.WEATHER_CACHE_KEY)).thenReturn("SNOW");

            TreeWeather weather = newClientWithApiResult(TreeWeather.SUNNY).getCurrentWeather();

            assertThat(weather).isEqualTo(TreeWeather.SNOW);
        }

        @Test
        @DisplayName("缓存 miss：回源取值并按配置 TTL 写缓存")
        void cacheMiss_fetchesAndWritesCache() {
            when(valueOperations.get(QWeatherClient.WEATHER_CACHE_KEY)).thenReturn(null);

            TreeWeather weather = newClientWithApiResult(TreeWeather.RAIN).getCurrentWeather();

            assertThat(weather).isEqualTo(TreeWeather.RAIN);
            verify(valueOperations).set(eq(QWeatherClient.WEATHER_CACHE_KEY),
                    eq("RAIN"), eq(Duration.ofMinutes(5)));
        }

        @Test
        @DisplayName("缓存脏值（非法枚举名）：降级为 miss 回源")
        void dirtyCacheValue_degradesToMiss() {
            when(valueOperations.get(QWeatherClient.WEATHER_CACHE_KEY))
                    .thenReturn("NOT_A_WEATHER");

            TreeWeather weather = newClientWithApiResult(TreeWeather.SNOW).getCurrentWeather();

            assertThat(weather).isEqualTo(TreeWeather.SNOW);
        }

        @Test
        @DisplayName("Redis 读异常：Fail-Open 降级为 miss 回源")
        void redisReadFailure_degradesToMiss() {
            when(valueOperations.get(QWeatherClient.WEATHER_CACHE_KEY))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));

            assertThat(newClientWithApiResult(TreeWeather.CLOUDY).getCurrentWeather())
                    .isEqualTo(TreeWeather.CLOUDY);
        }

        @Test
        @DisplayName("Redis 写异常：仅告警不影响返回值（Fail-Open）")
        void redisWriteFailure_doesNotAffectResult() {
            when(valueOperations.get(QWeatherClient.WEATHER_CACHE_KEY)).thenReturn(null);
            doThrow(new RedisConnectionFailureException("write failed"))
                    .when(valueOperations)
                    .set(anyString(), anyString(), any(Duration.class));

            assertThat(newClientWithApiResult(TreeWeather.SNOW).getCurrentWeather())
                    .isEqualTo(TreeWeather.SNOW);
        }
    }

    @Nested
    @DisplayName("TreeWeather.fromQWeatherText - 和风文本映射")
    class WeatherMappingTests {

        @ParameterizedTest(name = "[{index}] \"{0}\" → {1}")
        @CsvSource({
                "晴,        SUNNY",
                "多云,      CLOUDY",
                "阴,        CLOUDY",
                "雾,        CLOUDY",
                "小雨,      RAIN",
                "中雨,      RAIN",
                "雷阵雨,    RAIN",
                "小雪,      SNOW",
                "雨夹雪,    SNOW",
                "阵雪,      SNOW",
                "浮尘,      CLOUDY",
                "晴间多云,  SUNNY",
        })
        @DisplayName("和风 v7 常见天气现象文本正确映射")
        void qWeatherTextMapped(String text, TreeWeather expected) {
            assertThat(TreeWeather.fromQWeatherText(text)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "[{index}] null/空文本 → SUNNY")
        @NullAndEmptySource
        @DisplayName("null/空文本降级 SUNNY（不抛异常）")
        void nullOrBlank_degradesToSunny(String text) {
            assertThat(TreeWeather.fromQWeatherText(text)).isEqualTo(TreeWeather.SUNNY);
        }

        @Test
        @DisplayName("空白文本降级 SUNNY")
        void blankText_degradesToSunny() {
            assertThat(TreeWeather.fromQWeatherText("   ")).isEqualTo(TreeWeather.SUNNY);
        }
    }
}
