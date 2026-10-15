package com.cloudmart.software.vo.software;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论视图（方案 4.4/6.8/7.2）：两层展示；DELETED 占位不回传原内容；
 * HIDDEN 不回传原内容；PENDING 仅本人可见（status=SELF_PENDING）；
 * 根楼仅预览 3 条回复 + replyHasMore（不做不准确的 replyCount）。
 */
public record SoftwareCommentVO(
        Long id,
        Long parentId,
        Long userId,
        String nickname,
        String avatar,
        /** DELETED/HIDDEN 时为 null（占位语义由 status 表达） */
        String content,
        /** VISIBLE / SELF_PENDING / PENDING_PLACEHOLDER / DELETED_PLACEHOLDER / HIDDEN */
        String status,
        Integer likeCount,
        Boolean likedByMe,
        LocalDateTime createdAt,
        /** 仅根楼：回复预览（升序 ≤3 条，已过隐私过滤） */
        List<SoftwareCommentVO> replies,
        /** 仅根楼：是否存在预览之外的可见回复 */
        Boolean replyHasMore) {
}
