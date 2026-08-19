package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.wish.config.WishTreeEnvProperties;
import com.cloudmart.wish.entity.WishAiConversation;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.entity.WishWorldTreeState;
import com.cloudmart.wish.enums.AiConversationRole;
import com.cloudmart.wish.enums.AiScene;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.repository.WishAiConversationMapper;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.repository.WishWorldTreeStateMapper;
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
import java.time.LocalDateTime;
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
 * TreeEnvServiceImpl 单元测试（行为契约：状态流转/缓存/锁/降级）。
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
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(
                TreeEnvServiceImpl.SCAN_LOCK_KEY, "1", 240, TimeUnit.SECONDS)).thenReturn(true);
        treeEnvService = new TreeEnvServiceImpl(stateMapper, conversationMapper,
                interactionMapper, redisTemplate, new WishTreeEnvProperties(), new ObjectMapper());
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

    @Nested
    @DisplayName("getCurrentEnv - 状态查询")
    class GetCurrentEnvTests {

        @Test
        @DisplayName("无状态行：返回默认 SUNNY/INIT，mood=null")
        void noStateRow_returnsDefault() {
            when(stateMapper.selectById(STATE_ID)).thenReturn(null);
            TreeEnvVO vo = treeEnvService.getCurrentEnv();
            assertThat(vo.getEnvironment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(vo.getSource()).isEqualTo(TreeEnvSource.INIT);
            assertThat(vo.getMoodScore()).isNull();
        }

        @Test
        @DisplayName("有状态行 + Redis 缓存命中：填充 moodScore")
        void stateRowWithCache_fillsMoodScore() {
            WishWorldTreeState state = sunnyState();
            state.setSampleCount(8);
            when(stateMapper.selectById(STATE_ID)).thenReturn(state);
            when(valueOperations.get(TreeEnvServiceImpl.MOOD_CACHE_KEY))
                    .thenReturn("{\"score\":-0.65,\"computedAt\":\"2026-08-20T12:00:00\",\"sampleCount\":8}");

            TreeEnvVO vo = treeEnvService.getCurrentEnv();
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
                TreeEnvVO vo = treeEnvService.getCurrentEnv();
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
        @DisplayName("状态行不存在：自动初始化单行后正常流转")
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
}
