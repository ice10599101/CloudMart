package com.cloudmart.wish.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.dto.AdminAuditWishRequest;
import com.cloudmart.wish.dto.AdminWishListQuery;
import com.cloudmart.wish.vo.AdminWishStatsVO;
import com.cloudmart.wish.vo.AdminWishVO;

/**
 * 管理后台心愿服务接口。
 *
 * <p>提供心愿列表查看（offset 分页）和审核操作。</p>
 */
public interface AdminWishService {

    /**
     * 管理后台心愿列表（offset 分页）。
     *
     * <p>支持多维度筛选：userId / categoryId / status / auditStatus / visibility / keyword。
     * 管理后台需要跳页，故保留 offset 分页。</p>
     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<AdminWishVO> listWishes(AdminWishListQuery query);

    /**
     * 获取管理后台心愿详情。
     *
     * @param wishId 心愿 ID
     * @return 管理后台心愿 VO（含审核相关字段）
     */
    AdminWishVO getWishDetail(Long wishId);

    /**
     * 审核心愿。
     *
     * <p>业务规则：</p>
     * <ul>
     *   <li>PENDING → APPROVED：is_visible 设为 true</li>
     *   <li>PENDING → REJECTED：is_visible 设为 false，记录 reject_reason（Sprint 1.1 暂不持久化 reason，预留字段）</li>
     *   <li>已审核状态再次审核返回 409 WISH_STATUS_CONFLICT</li>
     *   <li>审核结果通过 RocketMQ wish-audited 事件通知作者</li>
     * </ul>
     *
     * @param wishId  心愿 ID
     * @param request 审核请求
     * @return 审核后的心愿 VO
     */
    AdminWishVO auditWish(Long wishId, AdminAuditWishRequest request);

    /**
     * 心愿宇宙综合统计（管理工作台数据源）。
     *
     * <p>统计口径：</p>
     * <ul>
     *   <li>总数类：全量心愿（软删已由 @TableLogic 过滤）按 status 分组计数</li>
     *   <li>今日类：created_at / checkin_date 落在服务器当前日期</li>
     * </ul>
     *
     * @return 统计 VO
     */
    AdminWishStatsVO stats();
}
