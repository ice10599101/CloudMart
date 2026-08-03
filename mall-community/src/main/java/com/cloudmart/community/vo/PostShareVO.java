package com.cloudmart.community.vo;

import java.time.LocalDateTime;

public record PostShareVO(
    Long id,
    Long postId,
    Long userId,
    String userNickname,
    String userAvatar,
    String channel,
    LocalDateTime createdAt
) {}
