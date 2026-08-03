package com.cloudmart.community.vo;

/**
 * 排行榜条目 VO。
 *
 * @param userId   用户ID
 * @param expValue 当月经验值
 * @param rankNo   排名（从1开始）
 */
public record RankingItemVO(
    Long userId,
    Integer expValue,
    Integer rankNo
) {}
