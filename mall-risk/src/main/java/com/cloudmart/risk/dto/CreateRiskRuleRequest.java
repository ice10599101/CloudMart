package com.cloudmart.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建风控规则请求")
public record CreateRiskRuleRequest(
    @Schema(description = "规则名称") @NotBlank String name,
    @Schema(description = "动作类型") @NotBlank String actionType,
    @Schema(description = "风险等级") @NotBlank String riskLevel,
    @Schema(description = "阈值") @NotNull Integer threshold,
    @Schema(description = "时间窗口(分钟)") @NotNull Integer timeWindowMinutes,
    @Schema(description = "状态: 0=启用, 1=禁用") Integer status,
    @Schema(description = "描述") String description
) {}
