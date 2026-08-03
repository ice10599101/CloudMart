package com.cloudmart.cart.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "购物车VO")
public record CartVO(
    @Schema(description = "购物车项列表") List<CartItemVO> items,
    @Schema(description = "总数量") Integer totalCount,
    @Schema(description = "总金额") BigDecimal totalAmount
) {}
