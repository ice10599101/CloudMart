package com.cloudmart.community.service.impl;

import com.cloudmart.community.service.CommunityCacheService;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.TagVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityCacheServiceImpl implements CommunityCacheService {

    private static final String POST_DETAIL_PREFIX = "community:post:detail:";
    private static final String FEED_PREFIX = "community:feed:";
    private static final String TRENDING_PREFIX = "community:trending:";
    private static final String HOT_SEARCH_PREFIX = "community:hot_search:";

    private static final Duration POST_TTL = Duration.ofMinutes(30);
    private static final Duration FEED_TTL = Duration.ofMinutes(5);
    private static final Duration TRENDING_TTL = Duration.ofMinutes(10);
    private static final Duration HOT_SEARCH_TTL = Duration.ofMinutes(15);

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public Optional<PostVO> getPostDetail(Long postId) {
        try {
            Object cached = redisTemplate.opsForValue().get(POST_DETAIL_PREFIX + postId);
            if (cached instanceof PostVO postVO) {
                return Optional.of(postVO);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for post detail {}: {}", postId, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void putPostDetail(Long postId, PostVO postVO) {
        try {
            redisTemplate.opsForValue().set(POST_DETAIL_PREFIX + postId, postVO, POST_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for post detail {}: {}", postId, e.getMessage());
        }
    }

    @Override
    public void evictPostDetail(Long postId) {
        try {
            redisTemplate.delete(POST_DETAIL_PREFIX + postId);
        } catch (Exception e) {
            log.warn("Redis cache evict failed for post detail {}: {}", postId, e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<PostVO>> getFeedPosts(String tab, int page, int size) {
        try {
            String key = FEED_PREFIX + tab + ":" + page + ":" + size;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof List<?> list) {
                return Optional.of((List<PostVO>) list);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for feed {}: {}", tab, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void putFeedPosts(String tab, int page, int size, List<PostVO> posts) {
        try {
            String key = FEED_PREFIX + tab + ":" + page + ":" + size;
            redisTemplate.opsForValue().set(key, posts, FEED_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for feed {}: {}", tab, e.getMessage());
        }
    }

    @Override
    public void evictFeedPosts() {
        try {
            var keys = redisTemplate.keys(FEED_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis cache evict failed for feed: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<TagVO>> getTrendingTopics(int limit) {
        try {
            Object cached = redisTemplate.opsForValue().get(TRENDING_PREFIX + limit);
            if (cached instanceof List<?> list) {
                return Optional.of((List<TagVO>) list);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for trending: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void putTrendingTopics(int limit, List<TagVO> topics) {
        try {
            redisTemplate.opsForValue().set(TRENDING_PREFIX + limit, topics, TRENDING_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for trending: {}", e.getMessage());
        }
    }

    @Override
    public void evictTrendingTopics() {
        try {
            var keys = redisTemplate.keys(TRENDING_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis cache evict failed for trending: {}", e.getMessage());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<String>> getHotSearches(int limit) {
        try {
            Object cached = redisTemplate.opsForValue().get(HOT_SEARCH_PREFIX + limit);
            if (cached instanceof List<?> list) {
                return Optional.of((List<String>) list);
            }
        } catch (Exception e) {
            log.warn("Redis cache read failed for hot searches: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void putHotSearches(int limit, List<String> searches) {
        try {
            redisTemplate.opsForValue().set(HOT_SEARCH_PREFIX + limit, searches, HOT_SEARCH_TTL);
        } catch (Exception e) {
            log.warn("Redis cache write failed for hot searches: {}", e.getMessage());
        }
    }

    @Override
    public void evictHotSearches() {
        try {
            var keys = redisTemplate.keys(HOT_SEARCH_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Redis cache evict failed for hot searches: {}", e.getMessage());
        }
    }
}
