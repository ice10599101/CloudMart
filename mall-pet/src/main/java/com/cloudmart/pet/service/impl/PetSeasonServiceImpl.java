package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonRanking;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonRankingMapper;
import com.cloudmart.pet.service.PetRankingService;
import com.cloudmart.pet.service.PetSeasonService;
import com.cloudmart.pet.vo.PetRankingVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 赛季查询服务（F2）：当前赛季实时榜（Top50）+ 我的名次 + 历届快照名次。
 *
 * <p>榜单口径与总排行榜一致（仅公开宠物，等级同分比经验）；实时名次走
 * {@link PetRankingService#ranking}（含 ZSet 缓存）；历史名次读结算快照表。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetSeasonServiceImpl implements PetSeasonService {

    private static final int TOP_N = 50;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonRankingMapper rankingMapper;
    private final PetMapper petMapper;
    private final PetRankingService rankingService;

    @Override
    public SeasonResult currentSeasonRanking(Long userId) {
        PetSeason season = seasonMapper.selectOne(new LambdaQueryWrapper<PetSeason>()
                .eq(PetSeason::getStatus, "ACTIVE")
                .orderByAsc(PetSeason::getEndsAt)
                .last("LIMIT 1"));
        if (season == null) {
            return new SeasonResult(null, List.of(), null, null);
        }
        // Top50：复用总排行榜等级榜（缓存优先 + is_public 过滤同口径），截取前 50
        PetRankingService.PetRankingResult levelRanking =
                rankingService.ranking(PetRankingService.RankingType.LEVEL, userId);
        List<PetRankingVO> top50 = levelRanking.top20().stream().limit(TOP_N).toList();

        Pet myPet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        return new SeasonResult(
                new SeasonInfo(season.getId(), season.getName(),
                        season.getStartsAt().toLocalDate().toString(),
                        season.getEndsAt().toLocalDate().toString(), season.getStatus()),
                top50,
                levelRanking.myRank(),
                myPet != null && myPet.getLevel() != null ? (long) myPet.getLevel() : null);
    }

    @Override
    public List<HistoryItem> myHistory(Long userId) {
        List<PetSeasonRanking> rows = rankingMapper.selectList(new LambdaQueryWrapper<PetSeasonRanking>()
                .eq(PetSeasonRanking::getUserId, userId)
                .orderByDesc(PetSeasonRanking::getId)
                .last("LIMIT 50"));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, PetSeason> seasonMap = new HashMap<>();
        seasonMapper.selectList(new LambdaQueryWrapper<PetSeason>()
                        .in(PetSeason::getId, rows.stream().map(PetSeasonRanking::getSeasonId).distinct().toList()))
                .forEach(season -> seasonMap.put(season.getId(), season));
        return rows.stream()
                .map(row -> {
                    PetSeason season = seasonMap.get(row.getSeasonId());
                    return new HistoryItem(row.getSeasonId(),
                            season != null ? season.getName() : String.valueOf(row.getSeasonId()),
                            season != null && season.getSettledAt() != null
                                    ? season.getSettledAt().atOffset(ZoneOffset.UTC).format(ISO) : null,
                            row.getRankNo() != null ? row.getRankNo() : 0,
                            row.getLevel() != null ? row.getLevel() : 1);
                })
                .toList();
    }
}
