package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 时区上报请求（文档 2.15，合规 26 时区策略）。
 *
 * <p>客户端登录/启动/时区变化时上报；offsetMinutes 仅用于服务端校验
 * （-720~840），不存储（IANA 时区已含 DST 信息）。</p>
 */
public record ReportTimezoneRequest(

        @Schema(description = "IANA 时区 ID", example = "Asia/Shanghai")
        @NotBlank(message = "时区不能为空")
        @Size(max = 32, message = "时区格式不正确")
        String timezone,

        @Schema(description = "UTC 偏移分钟数（-720~840）", example = "480")
        @NotNull(message = "偏移分钟数不能为空")
        @Min(value = -720, message = "偏移分钟数超出范围")
        @Max(value = 840, message = "偏移分钟数超出范围")
        Integer offsetMinutes
) {
}
