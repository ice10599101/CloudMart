package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 围栏创建/更新请求（Sprint 3.2 管理后台"地图画圈创建围栏"）。
 *
 * <p>center 经纬度由管理端提交、服务端 geohash7 编码存储；
 * radius 最小 10m（验收：半径 0 拒绝）。</p>
 */
@Schema(description = "围栏保存请求")
public record SaveFenceRequest(
        @Schema(description = "围栏名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "围栏名称不能为空")
        @Size(max = 60, message = "名称最长 60 字")
        String name,

        @Schema(description = "触发绽放的心愿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "心愿 ID 不能为空")
        Long wishId,

        @Schema(description = "中心纬度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "中心纬度不能为空")
        Double centerLat,

        @Schema(description = "中心经度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "中心经度不能为空")
        Double centerLng,

        @Schema(description = "半径（米，最小 10）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "半径不能为空")
        Integer radiusM,

        @Schema(description = "生效开始（UTC，可空=不限）")
        LocalDateTime validFrom,

        @Schema(description = "生效结束（UTC，可空=不限）")
        LocalDateTime validTo,

        @Schema(description = "是否启用（默认 true）")
        Boolean isActive
) {
}
