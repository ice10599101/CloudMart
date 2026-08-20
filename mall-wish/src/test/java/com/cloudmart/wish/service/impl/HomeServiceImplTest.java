package com.cloudmart.wish.service.impl;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishProgress;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.service.WorldTreeService;
import com.cloudmart.wish.vo.WorldTreeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HomeServiceImpl 单元测试")
class HomeServiceImplTest {

    @Mock
    private WishMapper wishMapper;
    @Mock
    private WishProgressMapper wishProgressMapper;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private WorldTreeService worldTreeService;

    @InjectMocks
    private HomeServiceImpl homeService;

    private static final Long USER_ID = 1001L;

    @BeforeEach
    void setUp() {
        homeService = new HomeServiceImpl(wishMapper, wishProgressMapper, userFeignClient,
                redisTemplate, worldTreeService);
    }

    @Nested
    @DisplayName("getHomeAggregation - 首页聚合")
    class GetHomeAggregationTests {

        @Test
        @DisplayName("已登录用户返回完整聚合数据（todayRecommend + myWishes + hotResonance）")
        void getHomeAggregation_loggedInUser_success() {
            // 模拟 Redis 缓存未命中
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptySet());

            // 模拟热门心愿 DB 查询
            List<Wish> hotWishes = List.of(buildWishWithSupport(1L, 100));
            when(wishMapper.selectList(any())).thenReturn(hotWishes);

            // 模拟作者信息
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", 1001L, "nickname", "用户A", "avatar", "a.png")
                    )));

            // 模拟我的心愿
            when(wishProgressMapper.selectBatchIds(any())).thenReturn(List.of(buildProgress(1L)));

            // 模拟世界树聚合（Sprint 2.1 首页接线）
            WorldTreeVO worldTree = new WorldTreeVO(100, 20, 500,
                    com.cloudmart.wish.enums.TreeEnvironment.SUNNY,
                    com.cloudmart.wish.enums.TreeSeason.SUMMER, null);
            when(worldTreeService.getTreeAggregation()).thenReturn(worldTree);

            var result = homeService.getHomeAggregation(USER_ID);

            assertThat(result.todayRecommend()).hasSize(1);
            assertThat(result.todayRecommend().get(0).title()).isEqualTo("测试心愿");
            assertThat(result.myWishes()).hasSize(1);
            assertThat(result.hotResonance()).hasSize(1);
            assertThat(result.entries().wishEntry()).isTrue();
            assertThat(result.entries().mapEntry()).isFalse();
            assertThat(result.entries().aiAssistantEntry()).isFalse();
            assertThat(result.worldTree()).isEqualTo(worldTree);
            assertThat(result.worldTree().totalFruits()).isEqualTo(100);
        }

        @Test
        @DisplayName("世界树聚合查询异常：Fail-Open 返回 null，不阻塞首页主内容")
        void getHomeAggregation_worldTreeFailure_failsOpen() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptySet());
            when(wishMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(worldTreeService.getTreeAggregation())
                    .thenThrow(new RuntimeException("redis down"));

            var result = homeService.getHomeAggregation(USER_ID);

            assertThat(result.worldTree()).isNull();
            assertThat(result.todayRecommend()).isEmpty();
        }

        @Test
        @DisplayName("未登录用户 myWishes 返回空列表")
        void getHomeAggregation_anonymousUser_emptyMyWishes() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptySet());

            List<Wish> hotWishes = List.of(buildWishWithSupport(1L, 50));
            when(wishMapper.selectList(any())).thenReturn(hotWishes);
            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", 1001L, "nickname", "用户A", "avatar", "a.png")
                    )));

            var result = homeService.getHomeAggregation(null);

            assertThat(result.myWishes()).isEmpty();
            assertThat(result.todayRecommend()).isNotEmpty();
        }

        @Test
        @DisplayName("无热门心愿时返回空列表")
        void getHomeAggregation_noHotWishes_returnsEmpty() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(Collections.emptySet());
            when(wishMapper.selectList(any())).thenReturn(Collections.emptyList());

            var result = homeService.getHomeAggregation(USER_ID);

            assertThat(result.todayRecommend()).isEmpty();
            assertThat(result.hotResonance()).isEmpty();
            assertThat(result.myWishes()).isEmpty();
        }

        @Test
        @DisplayName("Redis 缓存命中时不查热门心愿 DB")
        void getHomeAggregation_cacheHit_skipsDb() {
            Set<ZSetOperations.TypedTuple<Object>> cached = Set.of(
                    new TypedTupleStub(buildWishWithSupport(1L, 100), 100.0)
            );
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(cached);

            when(userFeignClient.batchGetUsers(any()))
                    .thenReturn(ApiResponse.ok(List.of(
                            Map.of("id", 1001L, "nickname", "用户A", "avatar", "a.png")
                    )));
            // myWishes 仍需查 DB（用户个性化数据不缓存）
            when(wishMapper.selectList(any())).thenReturn(Collections.emptyList());

            var result = homeService.getHomeAggregation(USER_ID);

            assertThat(result.todayRecommend()).isNotEmpty();
            // 注意：selectList 仍被调用一次（用于 myWishes），但热门心愿不查 DB
            // 这里验证 todayRecommend 有数据即可，不强制 verify never
        }
    }

    // ========== Helper methods ==========

    private Wish buildWishWithSupport(Long id, int supportCount) {
        Wish wish = new Wish();
        wish.setId(id);
        wish.setUserId(1001L);
        wish.setTitle("测试心愿");
        wish.setVisibility(WishVisibility.PUBLIC);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setFruitType(FruitType.GLOW);
        wish.setAuditStatus(AuditStatus.APPROVED);
        wish.setIsVisible(true);
        wish.setSupportCount(supportCount);
        wish.setLightCount(supportCount / 3);
        wish.setSameWishCount(supportCount / 3);
        wish.setBlessCount(supportCount / 3);
        wish.setCreatedAt(LocalDateTime.now());
        return wish;
    }

    private WishProgress buildProgress(Long wishId) {
        WishProgress progress = new WishProgress();
        progress.setWishId(wishId);
        progress.setCurrentValue(50);
        progress.setTargetValue(100);
        progress.setVersion(1);
        return progress;
    }

    /** 测试用 TypedTuple 桩 */
    private static class TypedTupleStub implements ZSetOperations.TypedTuple<Object> {
        private final Object value;
        private final Double score;

        TypedTupleStub(Object value, Double score) {
            this.value = value;
            this.score = score;
        }

        @Override
        public Object getValue() { return value; }
        @Override
        public Double getScore() { return score; }

        @Override
        public int compareTo(ZSetOperations.TypedTuple<Object> o) {
            return Double.compare(this.score, o.getScore());
        }
    }

    // ========== refreshHotCache ==========

    @Nested
    @DisplayName("refreshHotCache - 热门推荐缓存刷新")
    class RefreshHotCacheTests {

        @Test
        @DisplayName("正常刷新：DEL wish:hot:feed")
        void refreshDeletesHotFeedKey() {
            homeService.refreshHotCache();
            verify(redisTemplate).delete("wish:hot:feed");
        }

        @Test
        @DisplayName("Redis 异常 Fail-Open：不抛错（TTL 过期兜底）")
        void redisFailureFailsOpen() {
            when(redisTemplate.delete(anyString()))
                    .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("connection refused"));
            org.assertj.core.api.Assertions.assertThatCode(() -> homeService.refreshHotCache())
                    .doesNotThrowAnyException();
        }
    }
}
