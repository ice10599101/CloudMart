package com.cloudmart.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "风控规则VO")
public record RiskRuleVO(
    @Schema(description = "规则ID") Long id,
    @Schema(description = "规则名称") String name,
    @Schema(description = "规则类型") String type,
    @Schema(description = "阈值") Integer threshold,
    @Schema(description = "触发动作") String action,
    @Schema(description = "是否启用") Boolean enabled
) {}
