package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 轨迹上报请求（Sprint 3.3：坐标转 geohash6 入 Redis，原始坐标丢弃）。
 */
@Schema(description = "轨迹上报请求")
public record ReportTraceRequest(
        @Schema(description = "纬度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "纬度不能为空")
        Double lat,

        @Schema(description = "经度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "经度不能为空")
        Double lng
) {
}
