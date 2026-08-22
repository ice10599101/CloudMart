package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.entity.WishEnvConfig;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.entity.WishSpecialEvent;
import com.cloudmart.wish.entity.WishWorldTreeState;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.EnvCategory;
import com.cloudmart.wish.enums.SpecialEventStatus;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.enums.TreeTimePhase;
import com.cloudmart.wish.enums.TreeWeather;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishEnvConfigMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishSpecialEventMapper;
import com.cloudmart.wish.repository.WishWorldTreeStateMapper;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.cloudmart.wish.vo.TreeEnvVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TreeEnvServiceImpl 单元测试（行为契约：状态流转/缓存/锁/降级 + Sprint 2.2
 * 季节落库/特殊事件惰性过期/displayEnv 聚合优先级/时段时区）。
 * DB 真实读写断言见 TreeEnvIntegrationTest。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TreeEnvServiceImpl 单元测试")
class TreeEnvServiceImplTest {

    private static final long STATE_ID = WishWorldTreeState.SINGLETON_ID;

    @Mock
    private WishWorldTreeStateMapper stateMapper;
    @Mock
    private WishAiConversationMapper conversationMapper;
    @Mock
    private WishInteractionMapper interactionMapper;
    @Mock
    private WishSpecialEventMapper specialEventMapper;
    @Mock
    private WishEnvConfigMapper envConfigMapper;
    @Mock
    private QWeatherClient weatherClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private TreeEnvServiceImpl treeEnvService;

    @BeforeEach
    void setUp() {
        // 纯单测环境无 MyBatis-Plus 启动流程，手动初始化 Lambda Wrapper
        // 所需的实体列缓存（项目既有模式，见 TreeHoleServiceImplTest）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WishWorldTreeState.class);
        TableInfoHelper.initTableInfo(assistant, WishAiConversation.class);
        TableInfoHelper.initTableInfo(assistant, WishInteraction.class);
        TableInfoHelper.initTableInfo(assistant, WishSpecialEvent.class);
        TableInfoHelper.initTableInfo(assistant, WishEnvConfig.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(
                TreeEnvServiceImpl.SCAN_LOCK_KEY, "1", 240, TimeUnit.SECONDS)).thenReturn(true);
        // 天气为外部展示性数据，单测默认晴天；displayEnv 用例按需覆写
        lenient().when(weatherClient.getCurrentWeather()).thenReturn(TreeWeather.SUNNY);
        treeEnvService = new TreeEnvServiceImpl(stateMapper, conversationMapper,
                interactionMapper, specialEventMapper, envConfigMapper, weatherClient,
                redisTemplate, new WishTreeEnvProperties(), new ObjectMapper());
    }

    private WishWorldTreeState sunnyState() {
        WishWorldTreeState state = new WishWorldTreeState();
        state.setId(STATE_ID);
        state.setEnvironment(TreeEnvironment.SUNNY);
        state.setEnvironmentSource(TreeEnvSource.INIT);
        state.setSampleCount(0);
        return state;
    }

    private WishAiConversation assistantRecord(int sentimentScore, LocalDateTime createdAt) {
        WishAiConversation record = new WishAiConversation();
        record.setUserId(1001L);
        record.setSessionId("tree-hole-1-1001");
        record.setScene(AiScene.TREE_HOLE);
        record.setRole(AiConversationRole.ASSISTANT);
        record.setContent("回复内容");
        record.setSentimentScore(sentimentScore);
        record.setCreatedAt(createdAt);
        record.setUpdatedAt(createdAt);
        return record;
    }

    private WishSpecialEvent activeEvent(String eventCode, LocalDateTime expiresAt) {
        WishSpecialEvent event = new WishSpecialEvent();
        event.setId(9001L);
        event.setEventCode(eventCode);
        event.setTitle("流星雨");
        event.setDescription("全站流星划过树冠");
        event.setStatus(SpecialEventStatus.ACTIVE);
        event.setTriggeredBy(1L);
        event.setTriggeredAt(LocalDateTime.now().minusMinutes(5));
        event.setExpiresAt(expiresAt);
        return event;
    }

    @Nested
    @DisplayName("getCurrentEnv - 状态查询")
    class GetCurrentEnvTests {

        @Test
        @DisplayName("无状态行：返回默认 SUNNY/INIT，mood=null，季节实时计算兜底")
        void noStateRow_returnsDefault() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(null);
            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);
            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.INIT);
            assertThat(vo.getMoodScore()).isNull();
            assertThat(vo.getSeason()).isEqualTo(currentSeason());
        }

        @Test
        @DisplayName("有状态行 + Redis 缓存命中：填充 moodScore")
        void stateRowWithCache_fillsMoodScore() {
            WishWorldTreeState state = sunnyState();
            state.setSampleCount(8);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);
            when(valueOperations.get(TreeEnvServiceImpl.MOOD_CACHE_KEY))
                    .thenReturn("{\"score\":-0.65,\"computedAt\":\"2026-08-20T12:00:00\",\"sampleCount\":8}");

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);
            assertThat(vo.getMoodScore()).isEqualTo(-0.65);
            assertThat(vo.getSampleCount()).isEqualTo(8);
        }

        @Test
        @DisplayName("Redis 不可用：降级 mood=null，不抛异常（Fail-Open）")
        void redisFailure_degradesToNullMood() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            when(valueOperations.get(TreeEnvServiceImpl.MOOD_CACHE_KEY))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));

            assertThatCode(() -> {
                TreeEnvVO vo = treeEnvService.getCurrentEnv(null);
                assertThat(vo.getMoodScore()).isNull();
                assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("scan - 情绪扫描状态机")
    class ScanTests {

        @Test
        @DisplayName("负面情绪聚合：SUNNY→RAIN，写 mood 缓存并释放锁")
        void negativeMood_triggersRain() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            when(conversationMapper.selectList(any())).thenReturn(List.of(
                    assistantRecord(-70, LocalDateTime.now()),
                    assistantRecord(-65, LocalDateTime.now().minusMinutes(10))));
            when(interactionMapper.selectCount(any())).thenReturn(0L);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(vo.getTriggeredAt()).isNotNull();
            assertThat(vo.getExpiresAt()).isNull();
            assertThat(vo.getMoodScore()).isLessThan(-0.6);
            verify(valueOperations).set(eq(TreeEnvServiceImpl.MOOD_CACHE_KEY),
                    anyString(), any(Duration.class));
            verify(stateMapper).update(isNull(), any());
            verify(redisTemplate).delete(TreeEnvServiceImpl.SCAN_LOCK_KEY);
        }

        @Test
        @DisplayName("BLESS 突增（当前窗口 6 ≥ 前窗口 2×2 + 最小 5）：触发 RAINBOW")
        void blessBurst_triggersRainbow() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            when(conversationMapper.selectList(any())).thenReturn(List.of());
            when(interactionMapper.selectCount(any())).thenReturn(6L, 2L);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAINBOW);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.BLESS_BURST_RAINBOW);
            assertThat(vo.getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("BLESS 未达最小计数：不触发突增")
        void blessBelowMinCount_noBurst() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            when(conversationMapper.selectList(any())).thenReturn(List.of());
            when(interactionMapper.selectCount(any())).thenReturn(4L, 0L);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
        }

        @Test
        @DisplayName("锁被占用：跳过扫描直接返回当前状态")
        void scanLockHeld_skipsScan() {
            when(valueOperations.setIfAbsent(
                    TreeEnvServiceImpl.SCAN_LOCK_KEY, "1", 240, TimeUnit.SECONDS)).thenReturn(false);
            WishWorldTreeState state = sunnyState();
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            verify(conversationMapper, never()).selectList(any());
            verify(stateMapper, never()).update(any(), any());
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Redis 锁获取异常：Fail-Open 继续扫描")
        void scanLockRedisFailure_scansAnyway() {
            when(valueOperations.setIfAbsent(
                    TreeEnvServiceImpl.SCAN_LOCK_KEY, "1", 240, TimeUnit.SECONDS))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            when(conversationMapper.selectList(any())).thenReturn(List.of(
                    assistantRecord(-80, LocalDateTime.now())));
            when(interactionMapper.selectCount(any())).thenReturn(0L);

            TreeEnvVO vo = treeEnvService.scan();

            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
        }

        @Test
        @DisplayName("状态行不存在：自动初始化单行（含当前季节）后正常流转")
        void missingStateRow_initializesAndTransitions() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(null);
            when(conversationMapper.selectList(any())).thenReturn(List.of(
                    assistantRecord(-90, LocalDateTime.now())));
            when(interactionMapper.selectCount(any())).thenReturn(0L);

            TreeEnvVO vo = treeEnvService.scan();

            verify(stateMapper).insert(any(WishWorldTreeState.class));
            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.RAIN);
        }
    }

    @Nested
    @DisplayName("scanSeason - 季节落库")
    class ScanSeasonTests {

        @Test
        @DisplayName("季节变化：落库新季节（期望值按当前 UTC 日期动态计算，测试不随季节漂移）")
        void seasonChanged_persistsNewSeason() {
            WishWorldTreeState state = sunnyState();
            // 构造一个与当前季节不同的旧值（冬天/夏天对调保证相异）
            state.setSeason(currentSeason() == TreeSeason.WINTER
                    ? TreeSeason.SUMMER : TreeSeason.WINTER);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);

            TreeSeason result = treeEnvService.scanSeason();

            assertThat(result).isEqualTo(currentSeason());
            verify(stateMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("季节未变化：幂等不产生写")
        void seasonUnchanged_noWrite() {
            WishWorldTreeState state = sunnyState();
            state.setSeason(currentSeason());
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);

            TreeSeason result = treeEnvService.scanSeason();

            assertThat(result).isEqualTo(currentSeason());
            verify(stateMapper, never()).update(any(), any());
        }
    }

    @Nested
    @DisplayName("getActiveSpecialEvent - 特殊事件查询")
    class SpecialEventTests {

        @Test
        @DisplayName("活跃事件（未过期）：返回 VO")
        void activeEvent_returnsVO() {
            when(specialEventMapper.selectOne(any())).thenReturn(
                    activeEvent("METEOR_SHOWER", LocalDateTime.now().plusMinutes(30)));

            SpecialEventVO vo = treeEnvService.getActiveSpecialEvent();

            assertThat(vo).isNotNull();
            assertThat(vo.eventCode()).isEqualTo("METEOR_SHOWER");
            assertThat(vo.status()).isEqualTo(SpecialEventStatus.ACTIVE);
        }

        @Test
        @DisplayName("已过期事件：惰性写 ENDED 并返回 null")
        void expiredEvent_lazyEnds() {
            when(specialEventMapper.selectOne(any())).thenReturn(
                    activeEvent("AURORA", LocalDateTime.now().minusMinutes(1)));

            SpecialEventVO vo = treeEnvService.getActiveSpecialEvent();

            assertThat(vo).isNull();
            verify(specialEventMapper).update(isNull(), any());
        }

        @Test
        @DisplayName("无事件：返回 null")
        void noEvent_returnsNull() {
            when(specialEventMapper.selectOne(any())).thenReturn(null);

            assertThat(treeEnvService.getActiveSpecialEvent()).isNull();
        }
    }

    @Nested
    @DisplayName("displayEnv - 聚合展示优先级")
    class DisplayEnvTests {

        @Test
        @DisplayName("特殊事件 > 情绪 RAIN 与真实天气")
        void specialEventOverridesAll() {
            WishWorldTreeState state = sunnyState();
            state.setEnvironment(TreeEnvironment.RAIN);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);
            when(weatherClient.getCurrentWeather()).thenReturn(TreeWeather.SNOW);
            when(specialEventMapper.selectOne(any())).thenReturn(
                    activeEvent("METEOR_SHOWER", LocalDateTime.now().plusMinutes(30)));

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getDisplayEnv()).isEqualTo("METEOR_SHOWER");
            assertThat(vo.getSpecialEvent()).isNotNull();
        }

        @Test
        @DisplayName("情绪 RAINBOW > 真实天气（治愈叙事：收到祝福即时全站可见）")
        void moodRainbowOverWeather() {
            WishWorldTreeState state = sunnyState();
            state.setEnvironment(TreeEnvironment.RAINBOW);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);
            when(weatherClient.getCurrentWeather()).thenReturn(TreeWeather.CLOUDY);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getDisplayEnv()).isEqualTo("RAINBOW");
        }

        @Test
        @DisplayName("情绪 RAIN > 真实天气")
        void moodRainOverWeather() {
            WishWorldTreeState state = sunnyState();
            state.setEnvironment(TreeEnvironment.RAIN);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);
            when(weatherClient.getCurrentWeather()).thenReturn(TreeWeather.SUNNY);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getDisplayEnv()).isEqualTo("RAIN");
        }

        @Test
        @DisplayName("情绪 SUNNY（默认态）不覆盖真实天气")
        void sunnyMoodFallsBackToWeather() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            when(weatherClient.getCurrentWeather()).thenReturn(TreeWeather.SNOW);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getDisplayEnv()).isEqualTo("SNOW");
        }
    }

    @Nested
    @DisplayName("season/timePhase - 多维环境计算")
    class SeasonAndTimePhaseTests {

        @Test
        @DisplayName("season：state 落库值优先（未到次日扫描不改实时计算结果）")
        void seasonResolvedFromState() {
            WishWorldTreeState state = sunnyState();
            state.setSeason(TreeSeason.WINTER);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getSeason()).isEqualTo(TreeSeason.WINTER);
        }

        @Test
        @DisplayName("season：state.season=NULL 时实时计算兜底（Sprint 2.1 行为）")
        void seasonFallbackWhenNull() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());

            TreeEnvVO vo = treeEnvService.getCurrentEnv(null);

            assertThat(vo.getSeason()).isEqualTo(currentSeason());
        }

        @Test
        @DisplayName("timePhase：按客户端时区偏移计算（东八区 ≠ UTC 同刻时段由本地时刻决定）")
        void timePhaseComputedWithTzOffset() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());
            int eastEightOffset = 480;
            LocalTime expectedLocalTime = LocalTime.now(ZoneOffset.UTC).plusMinutes(eastEightOffset);

            TreeEnvVO vo = treeEnvService.getCurrentEnv(eastEightOffset);

            assertThat(vo.getTimePhase()).isEqualTo(TreeTimePhase.from(expectedLocalTime));
        }

        @Test
        @DisplayName("timePhase：非法时区偏移（超 UTC+14）按 UTC 处理不抛异常")
        void invalidTzOffset_treatedAsUtc() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(sunnyState());

            TreeEnvVO vo = treeEnvService.getCurrentEnv(99999);

            assertThat(vo.getTimePhase()).isEqualTo(TreeTimePhase.from(LocalTime.now(ZoneOffset.UTC)));
        }
    }

    @Nested
    @DisplayName("listActiveEnvConfigs - 环境配置查询")
    class ListEnvConfigsTests {

        @Test
        @DisplayName("返回配置 VO 列表（visual 解析为 JsonNode）")
        void mapsConfigVO() {
            WishEnvConfig meteor = new WishEnvConfig();
            meteor.setId(1L);
            meteor.setEnvCode("METEOR_SHOWER");
            meteor.setCategory(EnvCategory.SPECIAL_EVENT);
            meteor.setName("流星雨");
            meteor.setPriority(100);
            meteor.setVisual("{\"skyColor\":\"#0c1b3a\"}");
            meteor.setIsActive(true);
            when(envConfigMapper.selectList(any())).thenReturn(List.of(meteor));

            List<EnvConfigVO> configs = treeEnvService.listActiveEnvConfigs();

            assertThat(configs).hasSize(1);
            EnvConfigVO vo = configs.get(0);
            assertThat(vo.envCode()).isEqualTo("METEOR_SHOWER");
            assertThat(vo.category()).isEqualTo(EnvCategory.SPECIAL_EVENT);
            assertThat(vo.visual().path("skyColor").asText()).isEqualTo("#0c1b3a");
            assertThat(vo.isActive()).isTrue();
        }

        @Test
        @DisplayName("visual 脏数据：Fail-Open 降级 null 不阻断配置读取")
        void dirtyVisual_degradesToNull() {
            WishEnvConfig dirty = new WishEnvConfig();
            dirty.setId(2L);
            dirty.setEnvCode("BAD_JSON");
            dirty.setCategory(EnvCategory.WEATHER);
            dirty.setName("脏数据");
            dirty.setPriority(10);
            dirty.setVisual("not-a-json{{{");
            dirty.setIsActive(true);
            when(envConfigMapper.selectList(any())).thenReturn(List.of(dirty));

            List<EnvConfigVO> configs = treeEnvService.listActiveEnvConfigs();

            assertThat(configs).hasSize(1);
            assertThat(configs.get(0).visual()).isNull();
            assertThat(configs.get(0).envCode()).isEqualTo("BAD_JSON");
        }
    }

    /** 当前季节（UTC 日期判定；期望值动态计算保证测试四季可运行） */
    private TreeSeason currentSeason() {
        return TreeSeason.from(LocalDate.now(ZoneOffset.UTC));
    }
}
