package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateWishCommentRequest;
import com.cloudmart.wish.vo.WishCommentCreateVO;
import com.cloudmart.wish.vo.WishCommentVO;

import java.util.List;

/**
 * 心愿评论服务接口（文档 2.2 节，Sprint 1.2）。
 *
 * <p>审核策略（先发后审，文档 4.4 节）：评论立即可见（VISIBLE），
 * 敏感词命中仅标记（sensitive_hit=1）不阻断；管理后台下架后置 HIDDEN，四端立即不展示。</p>
 */
public interface WishCommentService {

    /**
     * 发表评论。
     *
     * @param userId  当前用户 ID
     * @param wishId  心愿 ID
     * @param request 评论请求（content 必填；parentId 回复目标）
     * @return 发表结果
     */
    WishCommentCreateVO createComment(Long userId, Long wishId, CreateWishCommentRequest request);

    /**
     * 评论列表（cursor 分页，时间倒序，仅 VISIBLE）。
     *
     * @param wishId   心愿 ID
     * @param viewerId 查看者 ID（PRIVATE/TREE_HOLE 心愿非作者 404）
     * @param cursor   游标（可空）
     * @param pageSize 每页数量
     * @return 分页结果
     */
    CommentPage listComments(Long wishId, Long viewerId, String cursor, Integer pageSize);

    /**
     * 删除自己的评论（软删，保留审计）。
     *
     * @param userId    当前用户 ID
     * @param wishId    心愿 ID
     * @param commentId 评论 ID
     */
    void deleteComment(Long userId, Long wishId, Long commentId);

    /**
     * 评论分页结果。
     *
     * @param records    当前页记录
     * @param nextCursor 下一页游标（无更多时为 null）
     * @param hasMore    是否还有更多
     */
    record CommentPage(List<WishCommentVO> records, String nextCursor, boolean hasMore) {
    }
}
