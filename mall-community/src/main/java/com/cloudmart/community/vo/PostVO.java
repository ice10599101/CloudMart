package com.cloudmart.community.vo;

import java.time.LocalDateTime;
import java.util.List;

public record PostVO(
    Long id,
    Long userId,
    String authorNickname,
    String authorAvatar,
    String title,
    String content,
    String coverImage,
    List<String> mediaUrls,
    String mediaType,
    Long categoryId,
    Long productId,
    Integer likeCount,
    Integer commentCount,
    Integer collectCount,
    Integer shareCount,
    Integer viewCount,
    Integer status,
    Integer reviewStatus,
    String reviewReason,
    Boolean isTop,
    List<TagVO> tags,
    Boolean isLiked,
    Boolean isCollected,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
