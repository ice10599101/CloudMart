package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 围栏打卡请求（文档 2.10：POST /wish/map/fence/check）。
 *
 * <p>坐标仅请求期间内存计算，不写 DB（隐私验收）。</p>
 */
@Schema(description = "围栏打卡请求")
public record FenceCheckRequest(
        @Schema(description = "触发绽放的心愿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "心愿 ID 不能为空")
        Long wishId,

        @Schema(description = "纬度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "纬度不能为空")
        Double lat,

        @Schema(description = "经度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "经度不能为空")
        Double lng
) {
}
