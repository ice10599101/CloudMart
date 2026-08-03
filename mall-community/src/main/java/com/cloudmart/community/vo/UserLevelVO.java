package com.cloudmart.community.vo;

public record UserLevelVO(
    Long userId,
    Integer level,
    Integer exp,
    Long totalExp,
    String levelTitle,
    String levelIcon,
    Integer nextLevelExp,
    String nextLevelTitle,
    Double expProgress
) {}
