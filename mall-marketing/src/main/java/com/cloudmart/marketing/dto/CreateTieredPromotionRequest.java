package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "创建阶梯满减活动请求")
public record CreateTieredPromotionRequest(
    @Schema(description = "活动名称") @NotBlank String name,
    @Schema(description = "活动描述") String description,
    @Schema(description = "活动开始时间") @NotNull LocalDateTime startTime,
    @Schema(description = "活动结束时间") @NotNull LocalDateTime endTime,
    @Schema(description = "阶梯规则列表") @NotNull List<TieredRuleRequest> rules
) {}
