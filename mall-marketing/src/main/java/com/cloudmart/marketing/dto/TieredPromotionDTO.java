package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "阶梯满减活动DTO")
public record TieredPromotionDTO(
    @Schema(description = "活动ID") Long id,
    @Schema(description = "活动名称") String name,
    @Schema(description = "活动描述") String description,
    @Schema(description = "活动开始时间") LocalDateTime startTime,
    @Schema(description = "活动结束时间") LocalDateTime endTime,
    @Schema(description = "状态: ENABLED/DISABLED/ENDED") String status,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "阶梯规则") List<TieredRuleDTO> rules
) {}
