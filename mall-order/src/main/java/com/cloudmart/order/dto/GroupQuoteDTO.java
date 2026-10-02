package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 拼团成团快照（T10）：mall-marketing 成团组的只读视图，mall-order
 * 建成团订单时校验状态并以 groupPrice 计价（拼团价权威）。
 */
@Schema(description = "拼团成团快照")
public record GroupQuoteDTO(
    Long groupOrderId,
    Long activityId,
    Long productId,
    Long skuId,
    BigDecimal groupPrice,
    List<Long> memberUserIds
) {
}
