package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * 秒杀报价快照（T09）：mall-order 经服务令牌回查的权威定价事实。
 * 订单以本快照的 seckillPrice 计价（活动报价），并校验主体/SKU/状态一致——
 * 秒杀价与普通价不混用，活动 A 不接受活动 B 的 SKU。
 */
@Schema(description = "秒杀报价快照（内部）")
public record SeckillQuoteDTO(

    @Schema(description = "请求ID")
    String requestId,

    @Schema(description = "用户ID（订单主体必须一致）")
    Long userId,

    @Schema(description = "活动ID")
    Long activityId,

    @Schema(description = "秒杀商品ID")
    Long productId,

    @Schema(description = "SKU ID（订单项必须一致）")
    Long skuId,

    @Schema(description = "购买数量")
    Integer quantity,

    @Schema(description = "冻结成交单价")
    BigDecimal seckillPrice,

    @Schema(description = "请求状态：PENDING 才可建单")
    String status,

    @Schema(description = "成功后关联的订单ID")
    Long orderId
) {
}
