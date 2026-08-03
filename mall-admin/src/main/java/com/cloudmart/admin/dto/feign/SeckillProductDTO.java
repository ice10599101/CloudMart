package com.cloudmart.admin.dto.feign;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀商品 Feign 传输对象，与 mall-seckill 服务端 SeckillProductDTO 字段对齐
 */
public record SeckillProductDTO(
    Long id,
    Long activityId,
    Long skuId,
    BigDecimal seckillPrice,
    BigDecimal originalPrice,
    Integer totalStock,
    Integer availableStock,
    Integer perUserLimit,
    String status,
    LocalDateTime createdAt
) {}
