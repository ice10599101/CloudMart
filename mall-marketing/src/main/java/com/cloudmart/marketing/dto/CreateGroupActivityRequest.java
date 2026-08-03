package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "创建拼团活动请求")
public record CreateGroupActivityRequest(
    @Schema(description = "活动名称") @NotBlank String name,
    @Schema(description = "活动描述") String description,
    @Schema(description = "商品ID") @NotNull Long productId,
    @Schema(description = "SKU ID") @NotNull Long skuId,
    @Schema(description = "原价") @NotNull BigDecimal originalPrice,
    @Schema(description = "拼团价") @NotNull BigDecimal groupPrice,
    @Schema(description = "成团所需人数") @NotNull @Min(2) Integer targetNumber,
    @Schema(description = "最大开团数，0=不限") @NotNull @Min(0) Integer maxGroups,
    @Schema(description = "每人限参团数") @NotNull @Min(1) Integer perUserLimit,
    @Schema(description = "活动开始时间") @NotNull LocalDateTime startTime,
    @Schema(description = "活动结束时间") @NotNull LocalDateTime endTime
) {}
