package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.vo.RankingSeasonVO;

/**
 * 排行榜管理后台服务。
 * <p>
 * 提供赛季管理功能，包括赛季列表查询（支持状态筛选）、赛季状态修改。
 * 当月排行榜和赛季榜单详情复用 {@link RankingService}。
 */
public interface AdminRankingService {

    /**
     * 分页查询赛季列表，支持按状态筛选。
     *
     * @param page   页码
     * @param size   每页数量
     * @param status 状态筛选（null 表示全部，0-进行中，1-已归档）
     * @return 赛季分页数据
     */
    Page<RankingSeasonVO> listSeasons(int page, int size, Integer status);

    /**
     * 修改赛季状态。
     *
     * @param seasonId 赛季ID
     * @param status   目标状态（0-进行中，1-已归档）
     */
    void updateSeasonStatus(Long seasonId, Integer status);
}
