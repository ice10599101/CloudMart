package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 陪伴心跳请求（三期）：前端在宠物页可见时按固定间隔上报已陪伴秒数。
 *
 * <p>服务端按日封顶（配置 {@code pet.intimacy.companionDailyCapSeconds}），
 * 超出的秒数不计——防止挂机刷亲密度；客户端只上报时长，不做任何数值计算。</p>
 */
@Schema(description = "陪伴心跳请求")
public record CompanionHeartbeatRequest(
        @Schema(description = "本次上报的陪伴秒数（1-600）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "缺少陪伴时长")
        @Min(value = 1, message = "陪伴时长非法")
        @Max(value = 600, message = "单次上报时长过大")
        Integer seconds,
        @Schema(description = "会话内单调递增序号（可选；提供时重复心跳按序号幂等去重，B05）")
        Long seq
) {
}
