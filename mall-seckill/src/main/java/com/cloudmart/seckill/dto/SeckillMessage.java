package com.cloudmart.seckill.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record SeckillMessage(
    Long userId,
    Long activityId,
    Long seckillProductId,
    Long skuId,
    BigDecimal seckillPrice,
    Integer quantity
) implements Serializable {
}
