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
    Long activityId,
    /** T03：报价 ID（报价下单必填；秒杀/内部直填路径为 null） */
    Long quoteId,
    /**
     * T09：秒杀请求 ID（秒杀下单必填；普通/报价下单为 null）。
     * 订单以此回查 mall-seckill 冻结快照计价（活动报价权威），并作为
     * 结果回写事件的关联键。
     */
    String seckillRequestId,
    /**
     * T10：拼团组 ID（成团建单必填；普通/报价下单为 null）。
     * 订单以此回查 mall-marketing 成团快照计价（拼团价权威）。
     */
    Long groupOrderId
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
