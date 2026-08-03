package com.cloudmart.community.vo;

import java.time.LocalDate;

/**
 * 排行榜赛季 VO。
 *
 * @param id        赛季ID
 * @param name      赛季名称
 * @param seasonKey 赛季标识（格式 yyyyMM）
 * @param startDate 开始日期
 * @param endDate   结束日期
 * @param status    状态：0-进行中，1-已归档
 */
public record RankingSeasonVO(
    Long id,
    String name,
    String seasonKey,
    LocalDate startDate,
    LocalDate endDate,
    Integer status
) {}
