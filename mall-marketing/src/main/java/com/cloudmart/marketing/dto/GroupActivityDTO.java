package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "拼团活动DTO")
public record GroupActivityDTO(
    @Schema(description = "活动ID") Long id,
    @Schema(description = "活动名称") String name,
    @Schema(description = "活动描述") String description,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "原价") BigDecimal originalPrice,
    @Schema(description = "拼团价") BigDecimal groupPrice,
    @Schema(description = "成团所需人数") Integer targetNumber,
    @Schema(description = "最大开团数") Integer maxGroups,
    @Schema(description = "当前已开团数") Integer currentGroups,
    @Schema(description = "每人限参团数") Integer perUserLimit,
    @Schema(description = "活动开始时间") LocalDateTime startTime,
    @Schema(description = "活动结束时间") LocalDateTime endTime,
    @Schema(description = "状态") String status,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
