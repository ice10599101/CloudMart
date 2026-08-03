package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(description = "添加秒杀商品请求")
public record AddSeckillProductRequest(

    @NotNull @Schema(description = "SKU ID")
    Long skuId,

    @NotNull @DecimalMin("0.01") @Schema(description = "秒杀价格")
    BigDecimal seckillPrice,

    @NotNull @DecimalMin("0.01") @Schema(description = "原价")
    BigDecimal originalPrice,

    @NotNull @Min(1) @Schema(description = "秒杀库存总量")
    Integer totalStock,

    @NotNull @Min(1) @Schema(description = "每人限购数量")
    Integer perUserLimit
) {}
