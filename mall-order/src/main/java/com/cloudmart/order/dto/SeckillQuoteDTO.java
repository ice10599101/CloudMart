package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 秒杀报价快照（T09）：mall-seckill 请求事实的只读视图，mall-order
 * 建单时校验主体/SKU/状态并以 seckillPrice 计价。
 */
@Schema(description = "秒杀报价快照")
public record SeckillQuoteDTO(
    String requestId,
    Long userId,
    Long activityId,
    Long productId,
    Long skuId,
    Integer quantity,
    BigDecimal seckillPrice,
    String status,
    Long orderId
) {
}
