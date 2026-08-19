package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 评论发表结果 VO（文档 2.2 节）。
 *
 * @param id        评论 ID
 * @param content   评论内容（转义后）
 * @param createdAt 评论时间
 */
@Schema(description = "评论发表结果")
public record WishCommentCreateVO(
        @Schema(description = "评论 ID") Long id,
        @Schema(description = "评论内容") String content,
        @Schema(description = "评论时间") LocalDateTime createdAt
) {
}
