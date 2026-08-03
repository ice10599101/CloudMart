package com.cloudmart.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "阶梯满减活动VO")
public record TieredPromotionVO(
    @Schema(description = "活动ID") Long id,
    @Schema(description = "活动名称") String name,
    @Schema(description = "类型") String type,
    @Schema(description = "开始时间") LocalDateTime startTime,
    @Schema(description = "结束时间") LocalDateTime endTime,
    @Schema(description = "状态") String status,
    @Schema(description = "阶梯规则") List<TieredRuleVO> rules
) {}
