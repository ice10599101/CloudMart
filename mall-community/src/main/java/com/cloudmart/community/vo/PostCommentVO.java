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
    LocalDateTime createdAt
) {}
