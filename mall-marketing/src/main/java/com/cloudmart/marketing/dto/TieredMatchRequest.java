package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

/**
 * R12：购物篮满减自动匹配请求（mall-order 报价链路调用）。
 * 当前阶梯满减为订单级（无商品范围维度），入参为购物篮商品总额。
 */
@Schema(description = "购物篮满减匹配请求")
public record TieredMatchRequest(
    @Schema(description = "购物篮商品金额合计") BigDecimal orderAmount
) {}
