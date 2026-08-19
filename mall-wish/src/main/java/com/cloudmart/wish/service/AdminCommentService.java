package com.cloudmart.wish.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.dto.AdminCommentListQuery;
import com.cloudmart.wish.dto.AdminCommentStatusRequest;
import com.cloudmart.wish.vo.AdminCommentVO;

/**
 * 管理后台评论服务（Sprint 1.2）。
 *
 * <p>提供评论审核查询（敏感词命中筛选）与上下架操作。
 * 下架后四端立即不展示；恢复上架重新可见。</p>
 */
public interface AdminCommentService {

    /**
     * 评论分页查询（offset 分页，含已软删记录，供审计）。
     *
     * @param query 查询参数
     * @return 评论分页
     */
    Page<AdminCommentVO> listComments(AdminCommentListQuery query);

    /**
     * 评论上下架。
     *
     * <p>HIDDEN：下架，普通用户列表立即不展示（但管理员仍可查看）；
     * VISIBLE：恢复上架。已是目标状态时返回 409 冲突。</p>
     *
     * @param commentId 评论 ID
     * @param request   目标状态
     * @return 更新后的评论
     */
    AdminCommentVO updateCommentStatus(Long commentId, AdminCommentStatusRequest request);
}
