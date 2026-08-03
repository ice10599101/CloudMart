package com.cloudmart.risk.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "风控记录DTO")
public record RiskRecordDTO(
    @Schema(description = "记录ID") Long id,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "动作类型") String actionType,
    @Schema(description = "风险等级") String riskLevel,
    @Schema(description = "结果") String result,
    @Schema(description = "规则ID") Long ruleId,
    @Schema(description = "详情") String detail,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "更新时间") LocalDateTime updatedAt
) {}
