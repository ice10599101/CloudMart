package com.cloudmart.community.service.impl;

import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.mq.CommunityEventProducer.LikeTimesMessage;
import com.cloudmart.community.service.LikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LikeServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private CommunityEventProducer communityEventProducer;

    private static final Long USER_ID = 1L;
    private static final Long TARGET_ID = 100L;
    private static final String TARGET_TYPE = "POST";

    private LikeService likeService() {
        return new LikeServiceImpl(redisTemplate, communityEventProducer);
    }

    private ZSetOperations.TypedTuple<String> mockTuple(String member, double score) {
        @SuppressWarnings("unchecked")
        ZSetOperations.TypedTuple<String> tuple = org.mockito.Mockito.mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(member);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }

    // ======================== like ========================

    @Nested
    @DisplayName("like")
    class LikeTests {

        @Test
        @DisplayName("should return true and update Redis when first like")
        void like_success() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.add(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(1L);
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

            boolean result = likeService().like(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isTrue();
            verify(setOperations).add(anyString(), eq(String.valueOf(USER_ID)));
            verify(redisTemplate, org.mockito.Mockito.times(2)).expire(anyString(), any());
            verify(zSetOperations).add(anyString(), eq(String.valueOf(TARGET_ID)), any(Double.class));
            verify(zSetOperations).incrementScore(anyString(), eq(String.valueOf(TARGET_ID)), eq(1.0));
        }

        @Test
        @DisplayName("should return false when already liked (SADD returns 0)")
        void like_alreadyLiked_returnsFalse() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.add(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(0L);

            boolean result = likeService().like(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isFalse();
            verify(redisTemplate, never()).opsForZSet();
            verify(redisTemplate, never()).expire(anyString(), any());
        }

        @Test
        @DisplayName("should return false when SADD returns null")
        void like_addReturnsNull_returnsFalse() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.add(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(null);

            boolean result = likeService().like(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isFalse();
            verify(redisTemplate, never()).opsForZSet();
        }
    }

    // ======================== unlike ========================

    @Nested
    @DisplayName("unlike")
    class UnlikeTests {

        @Test
        @DisplayName("should return true and update Redis when unlike existing like")
        void unlike_success() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.remove(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(1L);
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

            boolean result = likeService().unlike(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isTrue();
            verify(zSetOperations).remove(anyString(), eq(String.valueOf(TARGET_ID)));
            verify(zSetOperations).incrementScore(anyString(), eq(String.valueOf(TARGET_ID)), eq(-1.0));
        }

        @Test
        @DisplayName("should return false when not liked (SREM returns 0)")
        void unlike_notLiked_returnsFalse() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.remove(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(0L);

            boolean result = likeService().unlike(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isFalse();
            verify(redisTemplate, never()).opsForZSet();
        }
    }

    // ======================== isLiked ========================

    @Nested
    @DisplayName("isLiked")
    class IsLikedTests {

        @Test
        @DisplayName("should return true when SISMEMBER returns true")
        void isLiked_true() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.isMember(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(true);

            boolean result = likeService().isLiked(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when SISMEMBER returns false")
        void isLiked_false() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.isMember(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(false);

            boolean result = likeService().isLiked(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when SISMEMBER returns null")
        void isLiked_null_returnsFalse() {
            when(redisTemplate.opsForSet()).thenReturn(setOperations);
            when(setOperations.isMember(anyString(), eq(String.valueOf(USER_ID)))).thenReturn(null);

            boolean result = likeService().isLiked(USER_ID, TARGET_TYPE, TARGET_ID);

            assertThat(result).isFalse();
        }
    }

    // ======================== batchIsLiked ========================

    @Nested
    @DisplayName("batchIsLiked")
    class BatchIsLikedTests {

        @Test
        @DisplayName("should return correct map from Pipeline results")
        void batchIsLiked_normal() {
            when(redisTemplate.executePipelined(any(SessionCallback.class)))
                    .thenReturn(List.of(Boolean.TRUE, Boolean.FALSE, Boolean.TRUE));

            Map<Long, Boolean> result = likeService().batchIsLiked(
                    USER_ID, TARGET_TYPE, List.of(100L, 200L, 300L));

            assertThat(result).hasSize(3);
            assertThat(result.get(100L)).isTrue();
            assertThat(result.get(200L)).isFalse();
            assertThat(result.get(300L)).isTrue();
        }

        @Test
        @DisplayName("should return empty map for empty input")
        void batchIsLiked_emptyInput_returnsEmptyMap() {
            Map<Long, Boolean> result = likeService().batchIsLiked(USER_ID, TARGET_TYPE, Collections.emptyList());

            assertThat(result).isEmpty();
            verify(redisTemplate, never()).executePipelined(any(SessionCallback.class));
        }

        @Test
        @DisplayName("should handle null results from Pipeline")
        void batchIsLiked_nullResults() {
            when(redisTemplate.executePipelined(any(SessionCallback.class)))
                    .thenReturn(Arrays.asList(null, Boolean.TRUE));

            Map<Long, Boolean> result = likeService().batchIsLiked(USER_ID, TARGET_TYPE, List.of(100L, 200L));

            assertThat(result).hasSize(2);
            assertThat(result.get(100L)).isFalse();
            assertThat(result.get(200L)).isTrue();
        }
    }

    // ======================== getLikedTargetIds ========================

    @Nested
    @DisplayName("getLikedTargetIds")
    class GetLikedTargetIdsTests {

        @Test
        @DisplayName("should return parsed Longs from ZREVRANGE")
        void getLikedTargetIds_normal() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                    .thenReturn(new LinkedHashSet<>(Set.of("100", "200", "300")));

            List<Long> result = likeService().getLikedTargetIds(USER_ID, TARGET_TYPE, 1, 10);

            assertThat(result).hasSize(3);
            assertThat(result).containsExactlyInAnyOrder(100L, 200L, 300L);
        }

        @Test
        @DisplayName("should return empty list when ZREVRANGE returns null")
        void getLikedTargetIds_null_returnsEmpty() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong())).thenReturn(null);

            List<Long> result = likeService().getLikedTargetIds(USER_ID, TARGET_TYPE, 1, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should skip invalid targetId strings")
        void getLikedTargetIds_invalidString_skips() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                    .thenReturn(new LinkedHashSet<>(Set.of("100", "invalid")));

            List<Long> result = likeService().getLikedTargetIds(USER_ID, TARGET_TYPE, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(100L);
        }
    }

    // ======================== countLiked ========================

    @Nested
    @DisplayName("countLiked")
    class CountLikedTests {

        @Test
        @DisplayName("should return count from ZCARD")
        void countLiked_normal() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.zCard(anyString())).thenReturn(5L);

            long result = likeService().countLiked(USER_ID, TARGET_TYPE);

            assertThat(result).isEqualTo(5L);
        }

        @Test
        @DisplayName("should return 0 when ZCARD returns null")
        void countLiked_null_returnsZero() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.zCard(anyString())).thenReturn(null);

            long result = likeService().countLiked(USER_ID, TARGET_TYPE);

            assertThat(result).isZero();
        }
    }

    // ======================== syncLikedTimesToMQ ========================

    @Nested
    @DisplayName("syncLikedTimesToMQ")
    class SyncLikedTimesTests {

        @Test
        @DisplayName("should send MQ messages for non-zero deltas")
        void sync_normal() {
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(mockTuple("100", 1.0));
            tuples.add(mockTuple("200", -1.0));

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.popMin(anyString(), anyLong())).thenReturn(tuples);

            likeService().syncLikedTimesToMQ();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LikeTimesMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(communityEventProducer).publishLikeTimesBatch(captor.capture());

            List<LikeTimesMessage> messages = captor.getValue();
            assertThat(messages).hasSize(2);
            assertThat(messages).extracting(LikeTimesMessage::targetId)
                    .containsExactlyInAnyOrder(100L, 200L);
            assertThat(messages).extracting(LikeTimesMessage::delta)
                    .containsExactlyInAnyOrder(1, -1);
        }

        @Test
        @DisplayName("should not send MQ when ZSet is empty")
        void sync_empty_doesNothing() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.popMin(anyString(), anyLong())).thenReturn(Collections.emptySet());

            likeService().syncLikedTimesToMQ();

            verify(communityEventProducer, never()).publishLikeTimesBatch(any());
        }

        @Test
        @DisplayName("should skip items with zero delta")
        void sync_zeroDelta_skips() {
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(mockTuple("100", 0.0));
            tuples.add(mockTuple("200", 3.0));

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.popMin(anyString(), anyLong())).thenReturn(tuples);

            likeService().syncLikedTimesToMQ();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LikeTimesMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(communityEventProducer).publishLikeTimesBatch(captor.capture());

            List<LikeTimesMessage> messages = captor.getValue();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).targetId()).isEqualTo(200L);
            assertThat(messages.get(0).delta()).isEqualTo(3);
        }

        @Test
        @DisplayName("should skip items with null value or score")
        void sync_nullValues_skips() {
            @SuppressWarnings("unchecked")
            ZSetOperations.TypedTuple<String> nullValueTuple = org.mockito.Mockito.mock(ZSetOperations.TypedTuple.class);
            when(nullValueTuple.getValue()).thenReturn(null);
            when(nullValueTuple.getScore()).thenReturn(1.0);

            @SuppressWarnings("unchecked")
            ZSetOperations.TypedTuple<String> nullScoreTuple = org.mockito.Mockito.mock(ZSetOperations.TypedTuple.class);
            when(nullScoreTuple.getValue()).thenReturn("100");
            when(nullScoreTuple.getScore()).thenReturn(null);

            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(nullValueTuple);
            tuples.add(nullScoreTuple);

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.popMin(anyString(), anyLong())).thenReturn(tuples);

            likeService().syncLikedTimesToMQ();

            verify(communityEventProducer, never()).publishLikeTimesBatch(any());
        }

        @Test
        @DisplayName("should skip items with non-numeric targetId")
        void sync_invalidTargetId_skips() {
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(mockTuple("invalid", 1.0));
            tuples.add(mockTuple("100", 2.0));

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.popMin(anyString(), anyLong())).thenReturn(tuples);

            likeService().syncLikedTimesToMQ();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LikeTimesMessage>> captor = ArgumentCaptor.forClass(List.class);
            verify(communityEventProducer).publishLikeTimesBatch(captor.capture());

            List<LikeTimesMessage> messages = captor.getValue();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).targetId()).isEqualTo(100L);
        }
    }
}
