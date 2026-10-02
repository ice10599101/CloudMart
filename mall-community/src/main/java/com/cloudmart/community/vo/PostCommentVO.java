package com.cloudmart.community.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PostCommentVO(
    Long id,
    Long postId,
    Long userId,
    String authorNickname,
    String authorAvatar,
    Long parentId,
    Long replyToUserId,
    String replyToNickname,
    String content,
    Integer likeCount,
    Integer status,
    Boolean isLiked,
    List<PostCommentVO> replies,
    /** C04：该评论线程的回复总数（首页仅返回有限预览，超出走 replies 分页端点） */
    Integer replyCount,
    LocalDateTime createdAt
) {}
