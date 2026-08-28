package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 附近心愿条目（Sprint 3.1，文档 2.10 契约）。
 *
 * <p>approximateLat/Lng = geohash7 网格中心 + wishId 种子确定性偏移
 * （0-50m，可复现）——传输/展示环节均不含原始精确坐标（隐私验收）。</p>
 */
@Schema(description = "附近心愿（模糊化坐标）")
public record NearbyWishVO(
        Long wishId,
        String title,
        String fruitType,
        Double approximateLat,
        Double approximateLng,
        Integer distance,
        Integer lightCount,
        String geohash,
        LocalDateTime createdAt
) {
}
