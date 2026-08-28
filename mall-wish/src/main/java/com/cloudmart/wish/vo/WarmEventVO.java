package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 温暖事件条目（Sprint 3.2 城市幸福地图；坐标模糊化：geohash7 中心 +
 * eventId 种子确定性偏移）。
 */
@Schema(description = "温暖事件")
public record WarmEventVO(
        Long eventId,
        String title,
        String content,
        Double approximateLat,
        Double approximateLng,
        Integer distance,
        String geohash6,
        String cityCode,
        String nickname,
        LocalDateTime createdAt
) {
}
