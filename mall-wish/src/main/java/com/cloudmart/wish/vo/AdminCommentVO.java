package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.WishCommentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理后台评论 VO（Sprint 1.2）。
 *
 * @param id            评论 ID
 * @param wishId        心愿 ID
 * @param wishTitle     心愿标题
 * @param userId        评论用户 ID
 * @param nickname      评论用户昵称（Feign，降级占位）
 * @param content       评论内容
 * @param parentId      父评论 ID
 * @param status        状态（VISIBLE/HIDDEN）
 * @param sensitiveHit  是否命中敏感词
 * @param createdAt     评论时间
 * @param updatedAt     状态变更时间
 */
@Schema(description = "管理后台评论")
public record AdminCommentVO(
        @Schema(description = "评论 ID") Long id,
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "心愿标题") String wishTitle,
        @Schema(description = "评论用户 ID") Long userId,
        @Schema(description = "评论用户昵称") String nickname,
        @Schema(description = "评论内容") String content,
        @Schema(description = "父评论 ID") Long parentId,
        @Schema(description = "状态") WishCommentStatus status,
        @Schema(description = "是否命中敏感词") Boolean sensitiveHit,
        @Schema(description = "评论时间") LocalDateTime createdAt,
        @Schema(description = "状态变更时间") LocalDateTime updatedAt
) {
}
