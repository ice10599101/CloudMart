package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 温暖事件发布请求（Sprint 3.2 城市幸福地图 UGC）。
 */
@Schema(description = "温暖事件发布请求")
public record CreateWarmEventRequest(
        @Schema(description = "事件标题", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "标题不能为空")
        @Size(max = 60, message = "标题最长 60 字")
        String title,

        @Schema(description = "事件内容", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "内容不能为空")
        @Size(max = 500, message = "内容最长 500 字")
        String content,

        @Schema(description = "纬度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "纬度不能为空")
        Double lat,

        @Schema(description = "经度", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "经度不能为空")
        Double lng
) {
}
