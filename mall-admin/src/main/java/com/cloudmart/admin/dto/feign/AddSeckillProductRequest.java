package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 添加秒杀商品请求，与 mall-seckill 服务端 AddSeckillProductRequest 字段对齐
 */
public record AddSeckillProductRequest(
    @NotNull Long skuId,
    @NotNull @DecimalMin("0.01") BigDecimal seckillPrice,
    @NotNull @DecimalMin("0.01") BigDecimal originalPrice,
    @NotNull @Min(1) Integer totalStock,
    @NotNull @Min(1) Integer perUserLimit
) {}
