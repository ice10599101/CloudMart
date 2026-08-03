package com.cloudmart.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "风控检查请求")
public record RiskCheckRequest(
    @Schema(description = "用户ID") @NotNull Long userId,
    @Schema(description = "动作类型") @NotNull String actionType
) {}
