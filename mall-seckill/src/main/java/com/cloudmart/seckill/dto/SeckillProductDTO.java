package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "秒杀商品DTO")
public record SeckillProductDTO(

    @Schema(description = "秒杀商品ID")
    Long id,

    @Schema(description = "活动ID")
    Long activityId,

    @Schema(description = "SKU ID")
    Long skuId,

    @Schema(description = "秒杀价格")
    BigDecimal seckillPrice,

    @Schema(description = "原价")
    BigDecimal originalPrice,

    @Schema(description = "秒杀库存总量")
    Integer totalStock,

    @Schema(description = "剩余秒杀库存")
    Integer availableStock,

    @Schema(description = "每人限购数量")
    Integer perUserLimit,

    @Schema(description = "状态")
    String status,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {}
