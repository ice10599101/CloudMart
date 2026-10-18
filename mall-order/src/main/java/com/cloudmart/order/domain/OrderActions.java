package com.cloudmart.order.domain;

import java.util.List;

/**
 * T07：订单可用动作的唯一来源（服务端计算，三端只渲染，不各自编造规则）。
 *
 * <p>只覆盖订单生命周期动作；退款/售后是独立状态轴，其可用性（窗口、次数、可退金额）
 * 由 {@code /order/after-sale/eligibility} 按服务端规则判定，禁止塞进同一枚举。</p>
 *
 * <p>动作常量与前端 {@code OrderAction} 保持一致：
 * PAY（去支付）/ CANCEL（取消）/ CONFIRM_RECEIPT（确认收货）/ REVIEW（评价）。</p>
 */
public final class OrderActions {

    public static final String PAY = "PAY";
    public static final String CANCEL = "CANCEL";
    public static final String CONFIRM_RECEIPT = "CONFIRM_RECEIPT";
    public static final String REVIEW = "REVIEW";

    private OrderActions() {
    }

    /** 状态 → 可用动作；未知/终态返回空列表（前端不显示可写入口）。 */
    public static List<String> of(String status) {
        if (status == null) {
            return List.of();
        }
        return switch (status) {
            case "PENDING_PAYMENT" -> List.of(PAY, CANCEL);
            case "PAID" -> List.of();
            case "SHIPPED" -> List.of(CONFIRM_RECEIPT);
            case "COMPLETED" -> List.of(REVIEW);
            // CANCELLED / REFUNDING / REFUNDED 等：无订单级可写动作
            default -> List.of();
        };
    }
}
