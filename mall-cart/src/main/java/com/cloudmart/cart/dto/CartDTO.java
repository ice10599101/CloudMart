package com.cloudmart.cart.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "购物车DTO")
public record CartDTO(
    @Schema(description = "购物车项列表") List<CartItemDTO> items,
    @Schema(description = "总数量") Integer totalQuantity,
    @Schema(description = "总价格") BigDecimal totalPrice
) {}
