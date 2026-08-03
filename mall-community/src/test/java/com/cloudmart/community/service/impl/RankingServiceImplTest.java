package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.RankingRecord;
import com.cloudmart.community.entity.RankingSeason;
import com.cloudmart.community.repository.RankingRecordMapper;
import com.cloudmart.community.repository.RankingSeasonMapper;
import com.cloudmart.community.vo.RankingItemVO;
import com.cloudmart.community.vo.RankingSeasonVO;
import com.cloudmart.community.vo.UserRankingVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
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
class RankingServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private RankingSeasonMapper rankingSeasonMapper;

    @Mock
    private RankingRecordMapper rankingRecordMapper;

    private RankingServiceImpl rankingService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    @BeforeEach
    void setUp() {
        rankingService = new RankingServiceImpl(redisTemplate, rankingSeasonMapper, rankingRecordMapper);
    }

    private String currentMonthKey() {
        return "ranking:exp:monthly:" + YearMonth.now().format(MONTH_FMT);
    }

    private String lastMonthKey() {
        return "ranking:exp:monthly:" + YearMonth.now().minusMonths(1).format(MONTH_FMT);
    }

    private ZSetOperations.TypedTuple<String> mockTuple(String member, double score) {
        ZSetOperations.TypedTuple<String> tuple = org.mockito.Mockito.mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(member);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }

    @Nested
    @DisplayName("addExpToRanking")
    class AddExpToRankingTests {

        @Test
        @DisplayName("should increment score and set TTL")
        void addExpToRanking_success() {
            String key = currentMonthKey();
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

            rankingService.addExpToRanking(1L, 50);

            verify(zSetOperations).incrementScore(key, "1", 50.0);
            verify(redisTemplate).expire(eq(key), any(Duration.class));
        }

        @Test
        @DisplayName("should do nothing when userId is null")
        void addExpToRanking_nullUserId() {
            rankingService.addExpToRanking(null, 50);

            verify(redisTemplate, never()).opsForZSet();
        }

        @Test
        @DisplayName("should do nothing when exp is zero or negative")
        void addExpToRanking_nonPositiveExp() {
            rankingService.addExpToRanking(1L, 0);
            rankingService.addExpToRanking(1L, -10);

            verify(redisTemplate, never()).opsForZSet();
        }
    }

    @Nested
    @DisplayName("getMonthlyRanking")
    class GetMonthlyRankingTests {

        @Test
        @DisplayName("should return sorted ranking list")
        void getMonthlyRanking_success() {
            String key = currentMonthKey();
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(mockTuple("3", 300.0));
            tuples.add(mockTuple("1", 200.0));
            tuples.add(mockTuple("2", 100.0));

            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(key, 0, 9L)).thenReturn(tuples);

            List<RankingItemVO> result = rankingService.getMonthlyRanking(10);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).userId()).isEqualTo(3L);
            assertThat(result.get(0).expValue()).isEqualTo(300);
            assertThat(result.get(0).rankNo()).isEqualTo(1);
            assertThat(result.get(1).userId()).isEqualTo(1L);
            assertThat(result.get(1).rankNo()).isEqualTo(2);
            assertThat(result.get(2).userId()).isEqualTo(2L);
            assertThat(result.get(2).rankNo()).isEqualTo(3);
        }

        @Test
        @DisplayName("should return empty list when no data")
        void getMonthlyRanking_empty() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(Set.of());

            List<RankingItemVO> result = rankingService.getMonthlyRanking(10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when Redis returns null")
        void getMonthlyRanking_null() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(null);

            List<RankingItemVO> result = rankingService.getMonthlyRanking(10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should clamp size to max 100")
        void getMonthlyRanking_clampSize() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(99L)))
                    .thenReturn(Set.of());

            rankingService.getMonthlyRanking(500);

            verify(zSetOperations).reverseRangeWithScores(anyString(), eq(0L), eq(99L));
        }
    }

    @Nested
    @DisplayName("getUserRanking")
    class GetUserRankingTests {

        @Test
        @DisplayName("should return user rank and exp")
        void getUserRanking_success() {
            String key = currentMonthKey();
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRank(key, "1")).thenReturn(4L);
            when(zSetOperations.score(key, "1")).thenReturn(250.0);

            UserRankingVO result = rankingService.getUserRanking(1L);

            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.expValue()).isEqualTo(250);
            assertThat(result.rankNo()).isEqualTo(5);
        }

        @Test
        @DisplayName("should return rankNo=0 when user not in ranking")
        void getUserRanking_notInRanking() {
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRank(anyString(), anyString())).thenReturn(null);

            UserRankingVO result = rankingService.getUserRanking(1L);

            assertThat(result.userId()).isEqualTo(1L);
            assertThat(result.expValue()).isEqualTo(0);
            assertThat(result.rankNo()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return rankNo=0 when userId is null")
        void getUserRanking_nullUserId() {
            UserRankingVO result = rankingService.getUserRanking(null);

            assertThat(result.userId()).isNull();
            assertThat(result.expValue()).isEqualTo(0);
            assertThat(result.rankNo()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getSeasons")
    class GetSeasonsTests {

        @Test
        @DisplayName("should return paginated seasons")
        void getSeasons_success() {
            RankingSeason season = new RankingSeason();
            season.setId(1L);
            season.setName("2026年6月经验榜");
            season.setSeasonKey("202606");
            season.setStatus(1);

            Page<RankingSeason> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(season));
            when(rankingSeasonMapper.selectPage(any(Page.class), any())).thenReturn(page);

            Page<RankingSeasonVO> result = rankingService.getSeasons(1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).name()).isEqualTo("2026年6月经验榜");
            assertThat(result.getRecords().get(0).seasonKey()).isEqualTo("202606");
        }
    }

    @Nested
    @DisplayName("getSeasonRanking")
    class GetSeasonRankingTests {

        @Test
        @DisplayName("should return paginated ranking records")
        void getSeasonRanking_success() {
            RankingRecord record = new RankingRecord();
            record.setId(1L);
            record.setSeasonId(1L);
            record.setUserId(10L);
            record.setExpValue(500);
            record.setRankNo(1);

            Page<RankingRecord> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(record));
            when(rankingRecordMapper.selectPage(any(Page.class), any())).thenReturn(page);

            Page<RankingItemVO> result = rankingService.getSeasonRanking(1L, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).userId()).isEqualTo(10L);
            assertThat(result.getRecords().get(0).expValue()).isEqualTo(500);
            assertThat(result.getRecords().get(0).rankNo()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("persistLastMonthRanking")
    class PersistLastMonthRankingTests {

        @Test
        @DisplayName("should persist ranking to MySQL and delete Redis key")
        void persistLastMonthRanking_success() {
            String lastMonth = lastMonthKey();
            Set<ZSetOperations.TypedTuple<String>> tuples = new LinkedHashSet<>();
            tuples.add(mockTuple("3", 300.0));
            tuples.add(mockTuple("1", 200.0));
            tuples.add(mockTuple("2", 100.0));

            when(rankingSeasonMapper.selectCount(any())).thenReturn(0L);
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(lastMonth, 0L, -1L)).thenReturn(tuples);
            when(rankingSeasonMapper.insert(any(RankingSeason.class))).thenAnswer(invocation -> {
                RankingSeason season = invocation.getArgument(0);
                season.setId(1L);
                return 1;
            });

            rankingService.persistLastMonthRanking();

            ArgumentCaptor<RankingSeason> seasonCaptor = ArgumentCaptor.forClass(RankingSeason.class);
            verify(rankingSeasonMapper).insert(seasonCaptor.capture());
            RankingSeason savedSeason = seasonCaptor.getValue();
            assertThat(savedSeason.getStatus()).isEqualTo(1);
            assertThat(savedSeason.getSeasonKey())
                    .isEqualTo(YearMonth.now().minusMonths(1).format(MONTH_FMT));

            verify(rankingRecordMapper, org.mockito.Mockito.times(3)).insert(any(RankingRecord.class));
            verify(redisTemplate).delete(lastMonth);
        }

        @Test
        @DisplayName("should skip when season already archived")
        void persistLastMonthRanking_alreadyArchived() {
            when(rankingSeasonMapper.selectCount(any())).thenReturn(1L);

            rankingService.persistLastMonthRanking();

            verify(rankingSeasonMapper, never()).insert(any(RankingSeason.class));
            verify(rankingRecordMapper, never()).insert(any(RankingRecord.class));
            verify(redisTemplate, never()).delete(anyString());
        }

        @Test
        @DisplayName("should skip when Redis ZSet is empty")
        void persistLastMonthRanking_emptyZSet() {
            when(rankingSeasonMapper.selectCount(any())).thenReturn(0L);
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(Set.of());

            rankingService.persistLastMonthRanking();

            verify(rankingSeasonMapper, never()).insert(any(RankingSeason.class));
            verify(rankingRecordMapper, never()).insert(any(RankingRecord.class));
        }

        @Test
        @DisplayName("should skip when Redis returns null")
        void persistLastMonthRanking_nullZSet() {
            when(rankingSeasonMapper.selectCount(any())).thenReturn(0L);
            when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
            when(zSetOperations.reverseRangeWithScores(anyString(), anyLong(), anyLong()))
                    .thenReturn(null);

            rankingService.persistLastMonthRanking();

            verify(rankingSeasonMapper, never()).insert(any(RankingSeason.class));
            verify(rankingRecordMapper, never()).insert(any(RankingRecord.class));
        }
    }
}
