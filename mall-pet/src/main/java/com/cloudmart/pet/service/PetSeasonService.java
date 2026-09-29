package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetRankingVO;

import java.util.List;

/**
 * 排行榜赛季服务（F2）：当前赛季榜单 / 历届我的名次。
 */
public interface PetSeasonService {

    /** 当前赛季榜单结果：赛季信息 + Top50 + 我的最终/实时名次 */
    SeasonResult currentSeasonRanking(Long userId);

    /** 历届我的名次（已结算赛季，按结束时间倒序） */
    List<HistoryItem> myHistory(Long userId);

    /** 当前赛季 + Top50 + 我的名次聚合结果（myRank 为实时计算，SETTLED 赛季为快照名次） */
    record SeasonResult(SeasonInfo season, List<PetRankingVO> top50, Integer myRank, Long myLevel) {
    }

    /** 赛季信息 */
    record SeasonInfo(Long seasonId, String name, String startsAt, String endsAt, String status) {
    }

    /** 历届名次条目 */
    record HistoryItem(Long seasonId, String seasonName, String endedAt, int rankNo, int level) {
    }
}
