package com.cloudmart.community.service.impl;

import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.TagVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityCacheServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CommunityCacheServiceImpl communityCacheService;

    private static final Long POST_ID = 100L;

    @BeforeEach
    void setUp() {
        communityCacheService = new CommunityCacheServiceImpl(redisTemplate);
    }

    private PostVO buildPostVO() {
        return new PostVO(
                POST_ID, 1L, "author", "avatar.png", "Title", "Content",
                null, null, null, null, null,
                5, 2, 3, 1, 10, 1, 1, null, false,
                null, false, false, LocalDateTime.now(), null
        );
    }

    private TagVO buildTagVO() {
        return new TagVO(1L, "tech", "icon.png", 10, true, 1, LocalDateTime.now());
    }

    @Nested
    @DisplayName("getPostDetail")
    class GetPostDetailTests {

        @Test
        @DisplayName("should return cached post when found in Redis")
        void getPostDetail_cached() {
            PostVO postVO = buildPostVO();
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:post:detail:" + POST_ID)).thenReturn(postVO);

            Optional<PostVO> result = communityCacheService.getPostDetail(POST_ID);

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(POST_ID);
        }

        @Test
        @DisplayName("should return empty when post not in cache")
        void getPostDetail_notCached() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:post:detail:" + POST_ID)).thenReturn(null);

            Optional<PostVO> result = communityCacheService.getPostDetail(POST_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty when Redis throws exception")
        void getPostDetail_redisException() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis down"));

            Optional<PostVO> result = communityCacheService.getPostDetail(POST_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("putPostDetail")
    class PutPostDetailTests {

        @Test
        @DisplayName("should cache post detail with TTL")
        void putPostDetail_success() {
            PostVO postVO = buildPostVO();
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            communityCacheService.putPostDetail(POST_ID, postVO);

            verify(valueOperations).set(eq("community:post:detail:" + POST_ID), eq(postVO), any(java.time.Duration.class));
        }

        @Test
        @DisplayName("should not throw when Redis write fails")
        void putPostDetail_redisException_noThrow() {
            PostVO postVO = buildPostVO();
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                    () -> communityCacheService.putPostDetail(POST_ID, postVO)
            );
        }
    }

    @Nested
    @DisplayName("evictPostDetail")
    class EvictPostDetailTests {

        @Test
        @DisplayName("should delete post detail from cache")
        void evictPostDetail_success() {
            when(redisTemplate.delete("community:post:detail:" + POST_ID)).thenReturn(true);

            communityCacheService.evictPostDetail(POST_ID);

            verify(redisTemplate).delete("community:post:detail:" + POST_ID);
        }
    }

    @Nested
    @DisplayName("getFeedPosts")
    class GetFeedPostsTests {

        @Test
        @DisplayName("should return cached feed posts")
        void getFeedPosts_cached() {
            List<PostVO> posts = List.of(buildPostVO());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:feed:latest:1:10")).thenReturn(posts);

            Optional<List<PostVO>> result = communityCacheService.getFeedPosts("latest", 1, 10);

            assertThat(result).isPresent();
            assertThat(result.get()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when feed not cached")
        void getFeedPosts_notCached() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get(anyString())).thenReturn(null);

            Optional<List<PostVO>> result = communityCacheService.getFeedPosts("latest", 1, 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("putFeedPosts")
    class PutFeedPostsTests {

        @Test
        @DisplayName("should cache feed posts with TTL")
        void putFeedPosts_success() {
            List<PostVO> posts = List.of(buildPostVO());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            communityCacheService.putFeedPosts("latest", 1, 10, posts);

            verify(valueOperations).set(eq("community:feed:latest:1:10"), eq(posts), any(java.time.Duration.class));
        }
    }

    @Nested
    @DisplayName("evictFeedPosts")
    class EvictFeedPostsTests {

        @Test
        @DisplayName("should delete all feed cache keys")
        void evictFeedPosts_success() {
            Set<String> keys = Set.of("community:feed:latest:1:10", "community:feed:hot:1:10");
            when(redisTemplate.keys("community:feed:*")).thenReturn(keys);

            communityCacheService.evictFeedPosts();

            verify(redisTemplate).delete(keys);
        }

        @Test
        @DisplayName("should handle empty keys gracefully")
        void evictFeedPosts_noKeys() {
            when(redisTemplate.keys("community:feed:*")).thenReturn(Set.of());

            communityCacheService.evictFeedPosts();

            verify(redisTemplate, org.mockito.Mockito.never()).delete(any(Set.class));
        }
    }

    @Nested
    @DisplayName("getTrendingTopics")
    class GetTrendingTopicsTests {

        @Test
        @DisplayName("should return cached trending topics")
        void getTrendingTopics_cached() {
            List<TagVO> topics = List.of(buildTagVO());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:trending:10")).thenReturn(topics);

            Optional<List<TagVO>> result = communityCacheService.getTrendingTopics(10);

            assertThat(result).isPresent();
            assertThat(result.get()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when not cached")
        void getTrendingTopics_notCached() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:trending:10")).thenReturn(null);

            Optional<List<TagVO>> result = communityCacheService.getTrendingTopics(10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("putTrendingTopics")
    class PutTrendingTopicsTests {

        @Test
        @DisplayName("should cache trending topics with TTL")
        void putTrendingTopics_success() {
            List<TagVO> topics = List.of(buildTagVO());
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            communityCacheService.putTrendingTopics(10, topics);

            verify(valueOperations).set(eq("community:trending:10"), eq(topics), any(java.time.Duration.class));
        }
    }

    @Nested
    @DisplayName("getHotSearches")
    class GetHotSearchesTests {

        @Test
        @DisplayName("should return cached hot searches")
        void getHotSearches_cached() {
            List<String> searches = List.of("手机", "电脑");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:hot_search:10")).thenReturn(searches);

            Optional<List<String>> result = communityCacheService.getHotSearches(10);

            assertThat(result).isPresent();
            assertThat(result.get()).containsExactly("手机", "电脑");
        }

        @Test
        @DisplayName("should return empty when not cached")
        void getHotSearches_notCached() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.get("community:hot_search:10")).thenReturn(null);

            Optional<List<String>> result = communityCacheService.getHotSearches(10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("putHotSearches")
    class PutHotSearchesTests {

        @Test
        @DisplayName("should cache hot searches with TTL")
        void putHotSearches_success() {
            List<String> searches = List.of("手机");
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            communityCacheService.putHotSearches(10, searches);

            verify(valueOperations).set(eq("community:hot_search:10"), eq(searches), any(java.time.Duration.class));
        }
    }

    @Nested
    @DisplayName("evictHotSearches")
    class EvictHotSearchesTests {

        @Test
        @DisplayName("should delete all hot search cache keys")
        void evictHotSearches_success() {
            Set<String> keys = Set.of("community:hot_search:10");
            when(redisTemplate.keys("community:hot_search:*")).thenReturn(keys);

            communityCacheService.evictHotSearches();

            verify(redisTemplate).delete(keys);
        }
    }
}
