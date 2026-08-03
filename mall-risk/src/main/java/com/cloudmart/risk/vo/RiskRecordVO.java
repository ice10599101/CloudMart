package com.cloudmart.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "风控记录VO")
public record RiskRecordVO(
    @Schema(description = "记录ID") Long id,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "动作类型") String action,
    @Schema(description = "风险等级") String riskLevel,
    @Schema(description = "原因") String reason,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
