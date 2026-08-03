package com.cloudmart.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "拼团活动VO")
public record GroupActivityVO(
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
    @Schema(description = "状态") String status,
    @Schema(description = "开始时间") LocalDateTime startTime,
    @Schema(description = "结束时间") LocalDateTime endTime
) {}
