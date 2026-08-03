package com.cloudmart.community.vo;

/**
 * 用户当前排名 VO。
 *
 * @param userId   用户ID
 * @param expValue 当月经验值
 * @param rankNo   排名（从1开始，0 表示未上榜）
 */
public record UserRankingVO(
    Long userId,
    Integer expValue,
    Integer rankNo
) {}
