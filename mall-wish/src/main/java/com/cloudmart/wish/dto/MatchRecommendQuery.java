package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 匹配推荐查询（Sprint 2.6，文档 2.8：GET /wish/match/groups/recommend）。
 *
 * <p>keyword 精确命中优先；city 为同城代理（geohash 前缀 4 字符，与
 * 组 city_code 相等即同城命中）；两者皆空时退化为基于用户心愿标签的
 * 推荐（冷启动：无标签时按活跃度排序）。</p>
 *
 * @param keyword  关键词（可选）
 * @param city     同城代理码（可选）
 * @param cursor   游标（推荐结果窗口内偏移）
 * @param pageSize 页大小（默认 10，最大 50）
 */
@Schema(description = "匹配推荐查询")
public record MatchRecommendQuery(
        @Schema(description = "关键词（可选）") String keyword,
        @Schema(description = "同城代理码（可选，geohash 前缀）") String city,
        @Schema(description = "游标") String cursor,
        @Schema(description = "页大小") Integer pageSize
) {

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 50);
    }
}
