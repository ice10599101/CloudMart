package com.cloudmart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "秒杀商品VO")
public record SeckillProductVO(
    @Schema(description = "秒杀商品ID") Long id,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "商品图片") String productImage,
    @Schema(description = "秒杀价") BigDecimal seckillPrice,
    @Schema(description = "原价") BigDecimal originalPrice,
    @Schema(description = "可用库存") Integer availableStock,
    @Schema(description = "总库存") Integer totalStock,
    @Schema(description = "每人限购") Integer limitPerUser
) {}
