package com.cloudmart.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风控检查响应")
public record RiskCheckResponse(
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "动作类型") String actionType,
    @Schema(description = "风险等级") String riskLevel,
    @Schema(description = "结果") String result,
    @Schema(description = "规则ID") Long ruleId,
    @Schema(description = "详情") String detail
) {}
