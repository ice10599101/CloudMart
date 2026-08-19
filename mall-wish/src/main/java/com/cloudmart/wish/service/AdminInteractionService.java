package com.cloudmart.wish.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.dto.AdminInteractionListQuery;
import com.cloudmart.wish.vo.AdminInteractionVO;

/**
 * 管理后台互动服务（Sprint 1.2）。
 *
 * <p>提供互动记录审计查询：含已取消（软删）记录的完整轨迹，
 * 支持按心愿/用户/类型/时间范围多维筛选。</p>
 */
public interface AdminInteractionService {

    /**
     * 互动记录分页查询（offset 分页，含已取消记录）。
     *
     * @param query 查询参数
     * @return 互动记录分页（含软删，deletedAt 非空表示已取消）
     */
    Page<AdminInteractionVO> listInteractions(AdminInteractionListQuery query);
}
