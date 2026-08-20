package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.TreeFruitsQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishWorldTreeState;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishWorldTreeStateMapper;
import com.cloudmart.wish.service.WorldTreeService;
import com.cloudmart.wish.vo.WorldTreeVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WorldTreeServiceImpl 单元测试（行为契约：聚合缓存/防击穿/Fail-Open/分页/降级）。
 * DB 真实读写与口径断言见 TreeIntegrationTest。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorldTreeServiceImpl 单元测试")
class WorldTreeServiceImplTest {

    private static final String CACHE_KEY = WorldTreeServiceImpl.AGG_CACHE_KEY;
    private static final String LOCK_KEY = WorldTreeServiceImpl.AGG_LOCK_KEY;

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishWorldTreeStateMapper stateMapper;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private WorldTreeServiceImpl worldTreeService;

    @BeforeEach
    void setUp() {
        // 纯单测环境无 MyBatis-Plus 启动流程，手动初始化 Lambda Wrapper
        // 所需的实体列缓存（项目既有模式，见 TreeEnvServiceImplTest）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Wish.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.delete(anyString())).thenReturn(true);
        worldTreeService = new WorldTreeServiceImpl(
                wishMapper, stateMapper, userFeignClient, redisTemplate, new ObjectMapper());
    }

    // ========== getTreeAggregation：缓存命中 ==========

    @Nested
    @DisplayName("getTreeAggregation - 聚合缓存")
    class AggregationCacheTests {

        @Test
        @DisplayName("缓存命中 → 直接返回，不查 DB 计数")
        void cacheHit_returnsCachedCountsWithoutDbQuery() {
            when(valueOperations.get(CACHE_KEY))
                    .thenReturn("{\"totalFruits\":100,\"totalBloom\":30,\"totalLight\":2000}");

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(100);
            assertThat(vo.totalBloom()).isEqualTo(30);
            assertThat(vo.totalLight()).isEqualTo(2000);
            verify(wishMapper, never()).selectMaps(any());
        }

        @Test
        @DisplayName("缓存命中时环境/季节实时读取（不受计数缓存延迟影响）")
        void cacheHit_environmentReadFromStateTableRealtime() {
            when(valueOperations.get(CACHE_KEY))
                    .thenReturn("{\"totalFruits\":10,\"totalBloom\":2,\"totalLight\":50}");
            WishWorldTreeState state = new WishWorldTreeState();
            state.setEnvironment(TreeEnvironment.RAIN);
            state.setTriggeredAt(LocalDateTime.of(2026, 8, 21, 10, 0));
            when(stateMapper.selectById(WishWorldTreeState.SINGLETON_ID)).thenReturn(state);

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.environment()).isEqualTo(TreeEnvironment.RAIN);
            assertThat(vo.environmentUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 21, 10, 0));
            assertThat(vo.season()).isNotNull();
        }

        @Test
        @DisplayName("状态表无记录 → 环境默认 SUNNY")
        void noState_defaultsToSunny() {
            when(valueOperations.get(CACHE_KEY))
                    .thenReturn("{\"totalFruits\":1,\"totalBloom\":0,\"totalLight\":0}");
            when(stateMapper.selectById(WishWorldTreeState.SINGLETON_ID)).thenReturn(null);

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.environment()).isEqualTo(TreeEnvironment.SUNNY);
            assertThat(vo.environmentUpdatedAt()).isNull();
        }
    }

    // ========== getTreeAggregation：缓存 miss + 防击穿 ==========

    @Nested
    @DisplayName("getTreeAggregation - 防击穿回源")
    class StampedeProtectionTests {

        @Test
        @DisplayName("缓存 miss + 抢到锁 → 查 DB 并回填缓存（TTL 5min±30s 抖动）")
        void cacheMissWithLock_queriesDbAndBackfillsCache() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(true);
            when(wishMapper.selectMaps(any())).thenReturn(List.of(countsRow(88, 12, 660)));

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(88);
            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
            verify(valueOperations).set(eq(CACHE_KEY), anyString(), ttlCaptor.capture());
            // TTL = 5min ± 30s 抖动，下限 30s（Math.max 兜底）
            assertThat(ttlCaptor.getValue().toSeconds()).isBetween(30L, 330L);
            verify(redisTemplate).delete(LOCK_KEY);
        }

        @Test
        @DisplayName("缓存 miss + 未抢到锁 + 等待后重读命中 → 用他方回填值，不查 DB")
        void lockNotAcquired_waitsAndReadsBackfilledCache() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null,
                    "{\"totalFruits\":55,\"totalBloom\":5,\"totalLight\":100}");
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(false);

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(55);
            verify(wishMapper, never()).selectMaps(any());
            verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("缓存 miss + 未抢到锁 + 重读仍 miss → 直查 DB 保底（可用性优先）")
        void lockNotAcquired_rereadStillMiss_queriesDbDirectly() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(false);
            when(wishMapper.selectMaps(any())).thenReturn(List.of(countsRow(66, 6, 66)));

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(66);
            verify(wishMapper).selectMaps(any());
        }

        @Test
        @DisplayName("抢到锁后双重检查命中缓存（排队期间他方已回填）→ 不查 DB")
        void lockAcquired_doubleCheckHitsCache() {
            // 首次读 miss，抢锁成功后二次读命中
            when(valueOperations.get(CACHE_KEY)).thenReturn(null,
                    "{\"totalFruits\":44,\"totalBloom\":4,\"totalLight\":40}");
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(true);

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(44);
            verify(wishMapper, never()).selectMaps(any());
        }

        @Test
        @DisplayName("DB 聚合结果空行 → 计数归零不异常")
        void dbAggregateEmptyRow_returnsZeroCounts() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(true);
            when(wishMapper.selectMaps(any())).thenReturn(List.of(new HashMap<String, Object>()));

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isZero();
            assertThat(vo.totalBloom()).isZero();
            assertThat(vo.totalLight()).isZero();
        }
    }

    // ========== getTreeAggregation：Redis 异常 Fail-Open ==========

    @Nested
    @DisplayName("getTreeAggregation - Redis Fail-Open")
    class RedisFailureTests {

        @Test
        @DisplayName("Redis 读异常 → 降级直查 DB，不阻塞业务")
        void redisReadFailure_fallsBackToDb() {
            when(valueOperations.get(CACHE_KEY)).thenThrow(new RedisConnectionFailureException("connection refused"));
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                    .thenThrow(new RedisConnectionFailureException("connection refused"));
            when(wishMapper.selectMaps(any())).thenReturn(List.of(countsRow(7, 1, 9)));

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(7);
        }

        @Test
        @DisplayName("Redis 写（回填）异常 → 仅告警不抛，结果正常返回")
        void redisWriteFailure_stillReturnsResult() {
            when(valueOperations.get(CACHE_KEY)).thenReturn(null);
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(true);
            when(wishMapper.selectMaps(any())).thenReturn(List.of(countsRow(8, 2, 20)));
            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("connection refused"))
                    .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(8);
        }

        @Test
        @DisplayName("缓存脏数据（非法 JSON）→ 降级回源 DB")
        void corruptedCacheJson_fallsBackToDb() {
            when(valueOperations.get(CACHE_KEY)).thenReturn("not-a-json");
            when(valueOperations.setIfAbsent(LOCK_KEY, "1", 5, TimeUnit.SECONDS)).thenReturn(true);
            when(wishMapper.selectMaps(any())).thenReturn(List.of(countsRow(9, 3, 30)));

            WorldTreeVO vo = worldTreeService.getTreeAggregation();

            assertThat(vo.totalFruits()).isEqualTo(9);
        }
    }

    // ========== listFruits：分页语义 ==========

    @Nested
    @DisplayName("listFruits - 果实分页")
    class ListFruitsTests {

        @Test
        @DisplayName("返回条数超过 pageSize → hasMore=true 且 nextCursor=末条 id")
        void moreThanPageSize_returnsHasMoreAndCursor() {
            when(wishMapper.selectList(any())).thenReturn(List.of(
                    buildFruit(5L), buildFruit(4L), buildFruit(3L)));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(Map.of("id", 1001L, "nickname", "旅人甲"))));

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 2));

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isTrue();
            assertThat(page.nextCursor()).isEqualTo("4");
        }

        @Test
        @DisplayName("返回条数不超过 pageSize → hasMore=false 且 nextCursor=null")
        void notMoreThanPageSize_returnsEndOfPage() {
            when(wishMapper.selectList(any())).thenReturn(List.of(buildFruit(5L), buildFruit(4L)));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(Map.of("id", 1001L, "nickname", "旅人甲"))));

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 5));

            assertThat(page.records()).hasSize(2);
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();
        }

        @Test
        @DisplayName("空结果 → 空列表无游标（不调用 Feign）")
        void emptyResult_returnsEmptyWithoutFeignCall() {
            when(wishMapper.selectList(any())).thenReturn(List.of());

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 10));

            assertThat(page.records()).isEmpty();
            assertThat(page.hasMore()).isFalse();
            assertThat(page.nextCursor()).isNull();
            verify(userFeignClient, never()).batchGetUsers(any());
        }

        @Test
        @DisplayName("无效 cursor → 抛 WISH_VALIDATION_ERROR")
        void invalidCursor_throwsValidationError() {
            assertThatThrownBy(() -> worldTreeService.listFruits(
                    new TreeFruitsQuery("abc", null, null, null, null, 10)))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo(WishErrorCodes.WISH_VALIDATION_ERROR));
        }

        @Test
        @DisplayName("VO 映射：坐标/果实类型/点亮数/作者昵称完整")
        void fruitVoMapping_containsPositionAndAuthor() {
            Wish wish = buildFruit(9L);
            wish.setLightCount(36);
            wish.setFruitType(FruitType.BLOOM);
            when(wishMapper.selectList(any())).thenReturn(List.of(wish));
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(Map.of("id", 1001L, "nickname", "星星主人"))));

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 10));

            var fruit = page.records().get(0);
            assertThat(fruit.id()).isEqualTo(9L);
            assertThat(fruit.fruitType()).isEqualTo(FruitType.BLOOM);
            assertThat(fruit.lightCount()).isEqualTo(36);
            assertThat(fruit.authorNickname()).isEqualTo("星星主人");
            assertThat(fruit.position().theta()).isEqualTo(wish.getTreeTheta().doubleValue());
            assertThat(fruit.position().phi()).isEqualTo(wish.getTreePhi().doubleValue());
        }

        @Test
        @DisplayName("Feign 批量昵称失败 → 降级占位昵称（Fail-Open）")
        void feignFailure_fallsBackToPlaceholderNickname() {
            when(wishMapper.selectList(any())).thenReturn(List.of(buildFruit(1L)));
            when(userFeignClient.batchGetUsers(any())).thenThrow(new RuntimeException("feign timeout"));

            WorldTreeService.FruitPage page = worldTreeService.listFruits(
                    new TreeFruitsQuery(null, null, null, null, null, 10));

            assertThat(page.records()).hasSize(1);
            assertThat(page.records().get(0).authorNickname()).isEqualTo("心愿旅人");
        }

        @Test
        @DisplayName("pageSize 缺省/越界 → DTO 归一化为 50/100（构造器契约）")
        void pageSizeNormalization_appliedByDto() {
            assertThat(new TreeFruitsQuery(null, null, null, null, null, null).pageSize()).isEqualTo(50);
            assertThat(new TreeFruitsQuery(null, null, null, null, null, 0).pageSize()).isEqualTo(50);
            assertThat(new TreeFruitsQuery(null, null, null, null, null, 999).pageSize()).isEqualTo(100);
        }
    }

    // ========== 辅助方法 ==========

    /** DB 聚合行（selectMaps 返回结构：MySQL 列别名 → 数值） */
    private Map<String, Object> countsRow(long fruits, long bloom, long light) {
        Map<String, Object> row = new HashMap<>();
        row.put("total_fruits", fruits);
        row.put("total_bloom", bloom);
        row.put("total_light", light);
        return row;
    }

    /** 已固化坐标的公开心愿（果实） */
    private Wish buildFruit(Long id) {
        Wish wish = new Wish();
        wish.setId(id);
        wish.setUserId(1001L);
        wish.setTitle("世界树的果实");
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setFruitType(FruitType.GLOW);
        wish.setLightCount(0);
        wish.setTreeTheta(BigDecimal.valueOf(1.2345678));
        wish.setTreePhi(BigDecimal.valueOf(0.9876543));
        return wish;
    }
}
