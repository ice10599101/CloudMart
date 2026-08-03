package com.cloudmart.community.vo;

import java.util.List;

public record UserCommunityVO(
    Long userId,
    String nickname,
    String avatar,
    String signature,
    Long postCount,
    Long followCount,
    Long followerCount,
    Long collectCount,
    List<BadgeVO> badges,
    Boolean isFollowed
) {}
