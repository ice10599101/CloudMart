package com.cloudmart.community.vo;

import java.time.LocalDateTime;

public record CommentVO(
    Long id,
    Long postId,
    String postTitle,
    String content,
    Long parentId,
    String replyToNickname,
    Integer likeCount,
    Integer status,
    LocalDateTime createdAt
) {}
