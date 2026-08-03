package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.RankingRecord;
import com.cloudmart.community.entity.RankingSeason;
import com.cloudmart.community.repository.RankingRecordMapper;
import com.cloudmart.community.repository.RankingSeasonMapper;
import com.cloudmart.community.service.RankingService;
import com.cloudmart.community.vo.RankingItemVO;
import com.cloudmart.community.vo.RankingSeasonVO;
import com.cloudmart.community.vo.UserRankingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 排行榜服务实现，基于 Redis ZSet 维护实时榜单，MySQL 持久化历史赛季。
 *
 * <p>Redis Key 规范：{@code ranking:exp:monthly:{yyyyMM}}，TTL 35 天。
 * ZSet member 为 userId 字符串，score 为当月经验值。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingServiceImpl implements RankingService {

    private static final String RANKING_KEY_PREFIX = "ranking:exp:monthly:";
    private static final Duration RANKING_TTL = Duration.ofDays(35);
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MAX_PAGE_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final RankingSeasonMapper rankingSeasonMapper;
    private final RankingRecordMapper rankingRecordMapper;

    @Override
    public void addExpToRanking(Long userId, int exp) {
        if (userId == null || exp <= 0) {
            return;
        }
        String key = currentMonthKey();
        String member = userId.toString();
        redisTemplate.opsForZSet().incrementScore(key, member, exp);
        redisTemplate.expire(key, RANKING_TTL);
    }

    @Override
    public List<RankingItemVO> getMonthlyRanking(int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String key = currentMonthKey();
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, safeSize - 1L);

        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<RankingItemVO> result = new ArrayList<>(tuples.size());
        int rankNo = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            if (member == null) {
                continue;
            }
            Long userId = Long.valueOf(member);
            int expValue = tuple.getScore() != null ? tuple.getScore().intValue() : 0;
            result.add(new RankingItemVO(userId, expValue, rankNo++));
        }
        return result;
    }

    @Override
    public UserRankingVO getUserRanking(Long userId) {
        if (userId == null) {
            return new UserRankingVO(null, 0, 0);
        }
        String key = currentMonthKey();
        String member = userId.toString();

        Long rank = redisTemplate.opsForZSet().reverseRank(key, member);
        if (rank == null) {
            return new UserRankingVO(userId, 0, 0);
        }
        Double score = redisTemplate.opsForZSet().score(key, member);
        int expValue = score != null ? score.intValue() : 0;
        return new UserRankingVO(userId, expValue, rank.intValue() + 1);
    }

    @Override
    public Page<RankingSeasonVO> getSeasons(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<RankingSeason> result = rankingSeasonMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<RankingSeason>().orderByDesc(RankingSeason::getSeasonKey)
        );

        Page<RankingSeasonVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toSeasonVO).toList());
        return voPage;
    }

    @Override
    public Page<RankingItemVO> getSeasonRanking(Long seasonId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<RankingRecord> result = rankingRecordMapper.selectPage(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<RankingRecord>()
                        .eq(RankingRecord::getSeasonId, seasonId)
                        .orderByAsc(RankingRecord::getRankNo)
        );

        Page<RankingItemVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toItemVO).toList());
        return voPage;
    }

    @Override
    @Transactional
    public void persistLastMonthRanking() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String seasonKey = lastMonth.format(MONTH_FMT);

        Long existing = rankingSeasonMapper.selectCount(
                new LambdaQueryWrapper<RankingSeason>().eq(RankingSeason::getSeasonKey, seasonKey)
        );
        if (existing > 0) {
            log.info("赛季 {} 已归档，跳过持久化", seasonKey);
            return;
        }

        String redisKey = RANKING_KEY_PREFIX + seasonKey;
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(redisKey, 0, -1);

        if (tuples == null || tuples.isEmpty()) {
            log.info("赛季 {} 无榜单数据，跳过持久化", seasonKey);
            return;
        }

        List<RankingRecord> records = new ArrayList<>(tuples.size());
        int rankNo = 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String member = tuple.getValue();
            if (member == null) {
                continue;
            }
            RankingRecord record = new RankingRecord();
            record.setUserId(Long.valueOf(member));
            record.setExpValue(tuple.getScore() != null ? tuple.getScore().intValue() : 0);
            record.setRankNo(rankNo++);
            records.add(record);
        }

        if (records.isEmpty()) {
            log.info("赛季 {} 无有效记录，跳过持久化", seasonKey);
            return;
        }

        RankingSeason season = new RankingSeason();
        season.setName(lastMonth.getYear() + "年" + lastMonth.getMonthValue() + "月经验榜");
        season.setSeasonKey(seasonKey);
        season.setStartDate(lastMonth.atDay(1));
        season.setEndDate(lastMonth.atEndOfMonth());
        season.setStatus(1);
        rankingSeasonMapper.insert(season);

        for (RankingRecord record : records) {
            record.setSeasonId(season.getId());
            rankingRecordMapper.insert(record);
        }

        redisTemplate.delete(redisKey);
        log.info("赛季 {} 持久化完成，共 {} 条记录", seasonKey, records.size());
    }

    private String currentMonthKey() {
        return RANKING_KEY_PREFIX + YearMonth.now().format(MONTH_FMT);
    }

    private RankingSeasonVO toSeasonVO(RankingSeason season) {
        return new RankingSeasonVO(
                season.getId(),
                season.getName(),
                season.getSeasonKey(),
                season.getStartDate(),
                season.getEndDate(),
                season.getStatus()
        );
    }

    private RankingItemVO toItemVO(RankingRecord record) {
        return new RankingItemVO(record.getUserId(), record.getExpValue(), record.getRankNo());
    }
}
