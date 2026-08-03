package com.cloudmart.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风控检查VO")
public record RiskCheckVO(
    @Schema(description = "是否通过") Boolean passed,
    @Schema(description = "风险等级") String riskLevel,
    @Schema(description = "原因") String reason,
    @Schema(description = "规则名称") String ruleName
) {}
