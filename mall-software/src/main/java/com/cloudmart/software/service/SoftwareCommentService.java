package com.cloudmart.software.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.software.vo.software.SoftwareCommentVO;

/**
 * 软件评论（方案 4.4/6.8）：两层展示、社区机审+人工队列、双向拉黑过滤、
 * 唯一关系点赞。宿主不可见时全部评论 API 同步不可访问。
 */
public interface SoftwareCommentService {

    /** 创建评论（幂等）：机审绑定结果；VISIBLE 直发计数+事件，转人工 PENDING 仅本人可见 */
    SoftwareCommentVO create(Long userId, Long softwareId, String idempotencyKey,
                             String content, Long parentId, Long replyToCommentId);

    /** 删除本人评论：软删保留占位；可见评论删除计数-1 */
    void delete(Long userId, Long softwareId, Long commentId);

    /** 根评论分页（最新优先）：双向拉黑过滤后数量可能小于 pageSize；预览 3 条回复 */
    Page<SoftwareCommentVO> pageRoots(Long softwareId, Long viewerId, int page, int size);

    /** 楼中楼分页（升序）：继承宿主可见性与隐私过滤 */
    Page<SoftwareCommentVO> pageReplies(Long softwareId, Long rootId, Long viewerId, int page, int size);

    /** 幂等点赞（PUT）/取消（DELETE）：仅可见评论 */
    void like(Long userId, Long softwareId, Long commentId, boolean like);

    /**
     * 分享者置顶一个可见根评论（P1 4.4）：expectedVersion=CAS 评论版本；
     * 每软件最多一个置顶（主行锁保护），置顶校验归属为软件分享者。
     */
    void pin(Long userId, Long softwareId, Long commentId, Integer expectedVersion);

    /** 取消置顶（分享者本人；幂等：未置顶也成功） */
    void unpin(Long userId, Long softwareId, Long commentId, Integer expectedVersion);
}
