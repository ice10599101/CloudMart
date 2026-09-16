package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 漂流瓶评论（瓶下评论树节点）。
 *
 * <p>匿名评论不返回 userId/真实昵称头像；实名评论返回真实身份。
 * parentId 指向被回复评论；replyToNickname 为被回复人的展示昵称（匿名评论显示「匿名瓶友」）。</p>
 *
 * @param id              评论 ID
 * @param bottleId        漂流瓶 ID
 * @param userId          评论者用户 ID（匿名评论为 null）
 * @param nickname        评论者昵称（匿名评论为「匿名瓶友」）
 * @param avatar          评论者头像（匿名评论为 null）
 * @param parentId        被回复评论 ID（顶级评论为 null）
 * @param replyToNickname 被回复人昵称（顶级评论为 null）
 * @param content         评论内容（纯文本）
 * @param isAnonymous     评论是否匿名
 * @param createdAt       评论时间
 */
@Schema(description = "漂流瓶评论")
public record DriftBottleCommentVO(
        Long id,
        Long bottleId,
        Long userId,
        String nickname,
        String avatar,
        Long parentId,
        String replyToNickname,
        String content,
        Boolean isAnonymous,
        LocalDateTime createdAt
) {
}