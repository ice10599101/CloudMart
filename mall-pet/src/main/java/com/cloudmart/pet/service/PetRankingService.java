package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetRankingVO;

import java.util.List;

/**
 * 宠物排行榜服务（原文档 §80 社区深度融合：宠物排行榜）。
 */
public interface PetRankingService {

    /** 榜单维度 */
    enum RankingType {
        /** 等级榜 */
        LEVEL,
        /** 对战胜场榜 */
        BATTLE_WIN,
        /** 累计捞瓶榜 */
        BOTTLE
    }

    /**
     * 查询榜单（仅公开宠物入榜，Top 20）+ 我的数值/名次。
     *
     * @param type  榜单维度
     * @param userId 当前用户（计算我的名次）
     */
    PetRankingResult ranking(RankingType type, Long userId);

    /**
     * 每日全量重建排行榜缓存（P1-4：校准 ZSet 与 DB 间的漂移，由调度器凌晨触发）。
     *
     * @return 是否重建成功（Redis 异常时 false，读路径继续回落 DB）
     */
    boolean rebuildRankingCache();

    /** 榜单 + 我的名次聚合结果 */
    record PetRankingResult(List<PetRankingVO> top20, Long myValue, Integer myRank) {
    }
}
