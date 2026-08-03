package com.cloudmart.community.vo;

public record CheckInResultVO(
    Boolean checkedIn,
    Integer continuousDays,
    Integer expReward,
    Long totalExp,
    Integer currentLevel,
    String levelTitle,
    String levelIcon
) {}
