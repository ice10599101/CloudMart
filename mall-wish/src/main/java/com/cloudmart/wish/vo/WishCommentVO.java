package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 心愿评论 VO（文档 2.2 节）。
 *
 * <p>扁平结构 + parentId/replyToNickname 字段，层级由前端组装（时间倒序）。</p>
 *
 * @param id               评论 ID
 * @param wishId           心愿 ID
 * @param userId           评论用户 ID
 * @param nickname         评论用户昵称（Feign 获取，降级为占位值）
 * @param avatar           评论用户头像
 * @param content          评论内容（已转义存储，可直接渲染）
 * @param parentId         父评论 ID（顶级评论为 null）
 * @param replyToNickname  被回复用户昵称（回复时冗余展示）
 * @param createdAt        评论时间
 */
@Schema(description = "心愿评论")
public record WishCommentVO(
        @Schema(description = "评论 ID") Long id,
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "评论用户 ID") Long userId,
        @Schema(description = "评论用户昵称") String nickname,
        @Schema(description = "评论用户头像") String avatar,
        @Schema(description = "评论内容") String content,
        @Schema(description = "父评论 ID（顶级评论为 null）") Long parentId,
        @Schema(description = "被回复用户昵称") String replyToNickname,
        @Schema(description = "评论时间") LocalDateTime createdAt
) {
}
