package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 附近模式开关请求（Sprint 3.3：开启后客户端每 5 分钟上报一次坐标；
 * 关闭即时生效——Redis 开关键立即删除）。
 */
@Schema(description = "附近模式开关请求")
public record NearbyModeRequest(
        @Schema(description = "是否开启", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "enabled 不能为空")
        Boolean enabled
) {
}
