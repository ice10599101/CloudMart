package com.cloudmart.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新风控规则请求")
public record UpdateRiskRuleRequest(
    @Schema(description = "规则名称") String name,
    @Schema(description = "动作类型") String actionType,
    @Schema(description = "风险等级") String riskLevel,
    @Schema(description = "阈值") Integer threshold,
    @Schema(description = "时间窗口(分钟)") Integer timeWindowMinutes,
    @Schema(description = "状态: 0=启用, 1=禁用") Integer status,
    @Schema(description = "描述") String description
) {}
