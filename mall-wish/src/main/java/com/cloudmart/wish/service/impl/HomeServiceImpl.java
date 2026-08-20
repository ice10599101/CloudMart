package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishProgress;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.service.HomeService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.HomeAggregationVO;
import com.cloudmart.wish.vo.HomeEntriesVO;
import com.cloudmart.wish.vo.HotResonanceItemVO;
import com.cloudmart.wish.vo.MyWishSummaryVO;
import com.cloudmart.wish.vo.TodayRecommendItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 心愿宇宙首页聚合服务实现。
 *
 * <p>Sprint 1.1 简化版：</p>
 * <ul>
 *   <li>todayRecommend：近 7 天 PUBLIC + APPROVED，按 互动量 0.5 + 时效性 0.3 + 多样性 0.2 评分</li>
 *   <li>hotResonance：复用 todayRecommend 数据，按 support_count 降序取 Top 5</li>
 *   <li>myWishes：当前用户最近 3 条心愿摘要</li>
 *   <li>缓存：Redis ZSet {@code wish:hot:feed}（TTL 10min + 抖动 0-60s）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeServiceImpl implements HomeService {

    private static final String HOT_FEED_KEY = "wish:hot:feed";
    private static final long HOT_FEED_TTL_SECONDS = 600; // 10min
    private static final long HOT_FEED_JITTER_MAX_SECONDS = 60; // 0-60s

    private static final int TODAY_RECOMMEND_LIMIT = 5;
    private static final int HOT_RESONANCE_LIMIT = 5;
    private static final int MY_WISHES_LIMIT = 3;
    private static final int RECOMMEND_CANDIDATE_LIMIT = 50; // 候选集大小

    private final WishMapper wishMapper;
    private final WishProgressMapper wishProgressMapper;
    private final UserFeignClient userFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public HomeAggregationVO getHomeAggregation(Long userId) {
        // 今日推荐 + 热门共鸣（共用 ZSet 缓存）
        List<Wish> hotWishes = getHotWishes();
        List<TodayRecommendItemVO> todayRecommend = buildTodayRecommend(hotWishes);
        List<HotResonanceItemVO> hotResonance = buildHotResonance(hotWishes);

        // 我的心愿摘要（用户个性化，不缓存）
        List<MyWishSummaryVO> myWishes = buildMyWishes(userId);

        // Sprint 1.1 入口开关
        HomeEntriesVO entries = new HomeEntriesVO(true, false, false);

        return new HomeAggregationVO(
                null, // worldTree: Sprint 2.1 上线
                todayRecommend,
                myWishes,
                hotResonance,
                entries
        );
    }

    /**
     * 获取热门心愿列表（先查 Redis ZSet，未命中回源 DB 并回填）。
     */
    private List<Wish> getHotWishes() {
        // 尝试从 ZSet 缓存读取（Fail-Open：脏数据/Redis 故障时删键回源，不阻塞首页）
        List<Wish> cached = readHotFeedCache();
        if (!cached.isEmpty()) {
            log.debug("首页热门缓存命中, count={}", cached.size());
            return cached;
        }

        // 缓存未命中，回源 DB：近 7 天 PUBLIC + APPROVED
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minus(7, ChronoUnit.DAYS);
        List<Wish> candidates = wishMapper.selectList(
                new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                        .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                        .eq(Wish::getIsVisible, true)
                        .ge(Wish::getCreatedAt, sevenDaysAgo)
                        .orderByDesc(Wish::getSupportCount)
                        .last("LIMIT " + RECOMMEND_CANDIDATE_LIMIT)
        );

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 回填 ZSet 缓存（score = support_count，便于按互动量排序；写入失败仅告警）
        try {
            for (Wish wish : candidates) {
                redisTemplate.opsForZSet().add(HOT_FEED_KEY, wish, wish.getSupportCount());
            }
            long ttl = HOT_FEED_TTL_SECONDS + ThreadLocalRandom.current().nextLong(HOT_FEED_JITTER_MAX_SECONDS);
            redisTemplate.expire(HOT_FEED_KEY, ttl, TimeUnit.SECONDS);
            log.debug("首页热门缓存回填, count={}, ttl={}s", candidates.size(), ttl);
        } catch (Exception ex) {
            log.warn("首页热门缓存回填失败（Fail-Open，不影响响应）: {}", ex.getMessage());
        }

        return candidates;
    }

    private List<Wish> readHotFeedCache() {
        try {
            Set<ZSetOperations.TypedTuple<Object>> cached = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(HOT_FEED_KEY, 0, RECOMMEND_CANDIDATE_LIMIT - 1);
            if (cached == null || cached.isEmpty()) {
                return Collections.emptyList();
            }
            return cached.stream()
                    .map(tuple -> (Wish) tuple.getValue())
                    .filter(java.util.Objects::nonNull)
                    .toList();
        } catch (Exception ex) {
            // 脏数据（序列化格式不兼容/CCE）或 Redis 故障：清键降级回源
            log.warn("首页热门缓存读取失败，删除疑似脏键并回源 DB（Fail-Open）: {}", ex.getMessage());
            try {
                redisTemplate.delete(HOT_FEED_KEY);
            } catch (Exception delEx) {
                log.warn("脏键删除失败（键过期后自动消失）: {}", delEx.getMessage());
            }
            return Collections.emptyList();
        }
    }

    /**
     * 构建今日推荐 5 条（评分：互动量 0.5 + 时效性 0.3 + 多样性 0.2）。
     */
    private List<TodayRecommendItemVO> buildTodayRecommend(List<Wish> candidates) {
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量获取作者昵称
        Map<Long, String> nicknameMap = fetchNicknames(candidates);

        // 评分排序
        LocalDateTime now = LocalDateTime.now();
        List<ScoredWish> scored = candidates.stream()
                .map(w -> new ScoredWish(w, calculateScore(w, now, nicknameMap)))
                .sorted(Comparator.comparingDouble(ScoredWish::score).reversed())
                .limit(TODAY_RECOMMEND_LIMIT)
                .toList();

        return scored.stream()
                .map(sw -> {
                    Wish w = sw.wish();
                    List<String> mediaUrls = WishJsonUtils.parseStringList(w.getMediaUrls());
                    String coverUrl = mediaUrls.isEmpty() ? null : mediaUrls.get(0);
                    return new TodayRecommendItemVO(
                            w.getId(),
                            w.getTitle(),
                            coverUrl,
                            nicknameMap.getOrDefault(w.getUserId(), "心愿旅人"),
                            w.getSupportCount(),
                            w.getFruitType()
                    );
                })
                .toList();
    }

    /**
     * 计算推荐评分：互动量 0.5 + 时效性 0.3 + 多样性 0.2。
     */
    private double calculateScore(Wish wish, LocalDateTime now, Map<Long, String> nicknameMap) {
        // 互动量评分（0.5 权重）：归一化到 0-1
        double interactionScore = Math.min(1.0, wish.getSupportCount() / 100.0);

        // 时效性评分（0.3 权重）：7 天内衰减，越新越高
        long hoursAgo = ChronoUnit.HOURS.between(wish.getCreatedAt(), now);
        double recencyScore = Math.max(0, 1.0 - hoursAgo / (7.0 * 24));

        // 多样性评分（0.2 权重）：不同作者优先（简化：同作者降分）
        // Phase 1 简化版：使用 categoryId 多样性
        double diversityScore = 0.5; // 默认中等多样性

        return interactionScore * 0.5 + recencyScore * 0.3 + diversityScore * 0.2;
    }

    /**
     * 构建热门共鸣 5 条（复用 candidates，按 support_count 降序）。
     */
    private List<HotResonanceItemVO> buildHotResonance(List<Wish> candidates) {
        return candidates.stream()
                .sorted(Comparator.comparingInt(Wish::getSupportCount).reversed())
                .limit(HOT_RESONANCE_LIMIT)
                .map(w -> new HotResonanceItemVO(w.getId(), w.getTitle(), w.getSupportCount()))
                .toList();
    }

    /**
     * 构建我的心愿摘要 3 条。
     */
    private List<MyWishSummaryVO> buildMyWishes(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        List<Wish> myWishes = wishMapper.selectList(
                new LambdaQueryWrapper<Wish>()
                        .eq(Wish::getUserId, userId)
                        .orderByDesc(Wish::getCreatedAt)
                        .last("LIMIT " + MY_WISHES_LIMIT)
        );

        if (myWishes.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> wishIds = myWishes.stream().map(Wish::getId).toList();
        Map<Long, WishProgress> progressMap = wishProgressMapper.selectBatchIds(wishIds)
                .stream()
                .collect(Collectors.toMap(WishProgress::getWishId, p -> p));

        return myWishes.stream()
                .map(w -> {
                    WishProgress p = progressMap.get(w.getId());
                    int percentage = (p != null && p.getTargetValue() > 0)
                            ? Math.min(100, p.getCurrentValue() * 100 / p.getTargetValue())
                            : 0;
                    return new MyWishSummaryVO(w.getId(), w.getTitle(), w.getStatus(), percentage, w.getFruitType());
                })
                .toList();
    }

    private Map<Long, String> fetchNicknames(List<Wish> wishes) {
        Set<Long> userIds = wishes.stream().map(Wish::getUserId).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> (String) m.getOrDefault("nickname", "心愿旅人")
                        ));
            }
        } catch (Exception e) {
            log.warn("批量获取昵称失败，降级为占位: {}", e.getMessage());
        }
        return userIds.stream()
                .collect(Collectors.toMap(id -> id, id -> "心愿旅人"));
    }

    /** 评分包装记录。 */
    private record ScoredWish(Wish wish, double score) {}

    @Override
    public void refreshHotCache() {
        try {
            redisTemplate.delete(HOT_FEED_KEY);
            log.debug("热门推荐缓存已刷新: {}", HOT_FEED_KEY);
        } catch (Exception e) {
            // Fail-Open：删除失败不阻断任务，TTL 10min 自然过期兜底
            log.warn("热门推荐缓存刷新失败（Fail-Open，等待 TTL 过期兜底）: {}", e.getMessage());
        }
    }
}
