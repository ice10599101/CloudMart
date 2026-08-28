package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 地图网格聚合条目（Sprint 3.1，文档 2.10/3.1：同 geohash6 网格聚合 +
 * 数量角标；坐标为网格中心，不返回单个精确点——隐私验收）。
 */
@Schema(description = "地图网格聚合")
public record MapClusterVO(
        String geohash6,
        Double centerLat,
        Double centerLng,
        Integer count
) {
}
