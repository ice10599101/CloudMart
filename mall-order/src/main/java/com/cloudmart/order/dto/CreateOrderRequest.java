package com.cloudmart.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
    @NotBlank(message = "requestId不能为空") String requestId,
    @NotEmpty(message = "订单项不能为空") List<OrderItemInput> items,
    String receiverName,
    String receiverPhone,
    String receiverAddress,
    Long couponId,
    Long activityId
) {
    public record OrderItemInput(
        Long productId,
        @NotNull Long skuId,
        @NotNull @Positive(message = "数量必须为正数") Integer quantity,
        String productName,
        String skuImage,
        String skuAttributes,
        @NotNull @Positive(message = "价格必须为正数") BigDecimal price
    ) {}
}
