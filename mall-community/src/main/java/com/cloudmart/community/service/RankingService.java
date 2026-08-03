package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.vo.RankingItemVO;
import com.cloudmart.community.vo.RankingSeasonVO;
import com.cloudmart.community.vo.UserRankingVO;

import java.util.List;

/**
 * 排行榜服务，基于 Redis ZSet 实现实时榜单，MySQL 持久化历史数据。
 */
public interface RankingService {

    /**
     * 向当月榜单增加经验值（由 GrowthServiceImpl.addExp 调用）。
     *
     * @param userId 用户ID
     * @param exp    经验增量（正数）
     */
    void addExpToRanking(Long userId, int exp);

    /**
     * 获取当月经验榜单 Top N。
     *
     * @param size 榜单大小
     * @return 按排名升序排列的榜单条目
     */
    List<RankingItemVO> getMonthlyRanking(int size);

    /**
     * 获取当前用户当月排名。
     *
     * @param userId 用户ID
     * @return 用户排名信息，未上榜时 rankNo=0
     */
    UserRankingVO getUserRanking(Long userId);

    /**
     * 分页查询历史赛季列表。
     *
     * @param page 页码
     * @param size 每页数量
     * @return 赛季分页数据
     */
    Page<RankingSeasonVO> getSeasons(int page, int size);

    /**
     * 分页查询指定赛季的榜单记录。
     *
     * @param seasonId 赛季ID
     * @param page     页码
     * @param size     每页数量
     * @return 榜单条目分页数据
     */
    Page<RankingItemVO> getSeasonRanking(Long seasonId, int page, int size);

    /**
     * 持久化上个月榜单数据到 MySQL，并归档赛季。
     * 由定时任务在每月初调用。
     */
    void persistLastMonthRanking();
}
