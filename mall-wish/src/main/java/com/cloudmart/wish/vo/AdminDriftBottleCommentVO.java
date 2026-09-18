package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端漂流瓶评论 VO（审核视角）：包含评论者真实用户 ID，不受匿名规则脱敏。
 *
 * @param id          评论 ID
 * @param bottleId    漂流瓶 ID
 * @param userId      评论者用户 ID（真实身份）
 * @param nickname    评论者昵称（Feign 失败降级占位）
 * @param parentId    父评论 ID（顶级评论为 null）
 * @param content     评论内容
 * @param isAnonymous 评论是否匿名
 * @param createdAt   评论时间
 */
@Schema(description = "管理端漂流瓶评论")
public record AdminDriftBottleCommentVO(
        Long id,
        Long bottleId,
        Long userId,
        String nickname,
        Long parentId,
        String content,
        Boolean isAnonymous,
        LocalDateTime createdAt
) {
}
