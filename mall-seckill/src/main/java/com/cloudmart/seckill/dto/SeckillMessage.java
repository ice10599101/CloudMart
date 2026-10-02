package com.cloudmart.seckill.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 秒杀下单消息（T09）：requestId 由提交时生成并贯穿全链路——订单侧以它作为
 * 订单 request_key 幂等键，重复投递不再生成随机键；seckillPrice 为请求事实表
 * 冻结的成交价快照（真实定价以 mall-order 向 mall-seckill 回查校验为准，
 * 消息值仅作展示冗余）。
 */
public record SeckillMessage(
    String requestId,
    Long userId,
    Long activityId,
    Long seckillProductId,
    Long skuId,
    BigDecimal seckillPrice,
    Integer quantity
) implements Serializable {
}
