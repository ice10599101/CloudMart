package com.cloudmart.wish.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.dto.AdminBottleListQuery;
import com.cloudmart.wish.vo.AdminDriftBottleCommentVO;
import com.cloudmart.wish.vo.AdminDriftBottleDashboardVO;
import com.cloudmart.wish.vo.AdminDriftBottleVO;

import java.util.List;

/**
 * 管理后台漂流瓶服务：列表 / 详情 / 下架恢复 / 数据看板。
 *
 * <p>管理端 VO 包含真实用户 ID（治理溯源），不受匿名规则脱敏；
 * 下架为软隐藏（is_hidden），用户端不可见但数据保留。</p>
 */
public interface AdminDriftBottleService {

    /** 漂流瓶列表（offset 分页）：物理状态 / 用户（投瓶人或捞瓶人）/ 关键词筛选 */
    Page<AdminDriftBottleVO> listBottles(AdminBottleListQuery query);

    /** 漂流瓶详情（含瓶下评论全量，真实身份） */
    AdminDriftBottleDetail detail(Long bottleId);

    /** 下架/恢复（软隐藏：is_hidden） */
    AdminDriftBottleVO updateHidden(Long bottleId, boolean isHidden);

    /** 数据看板：状态分布 / 今日活动 / 近 14 天趋势 / 投瓶榜 */
    AdminDriftBottleDashboardVO dashboard();

    /**
     * 漂流瓶详情聚合。
     *
     * @param bottle   漂流瓶（管理视角）
     * @param comments 瓶下评论（真实身份，id 倒序）
     */
    record AdminDriftBottleDetail(AdminDriftBottleVO bottle, List<AdminDriftBottleCommentVO> comments) {
    }
}
