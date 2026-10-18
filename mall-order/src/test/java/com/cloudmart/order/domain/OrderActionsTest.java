package com.cloudmart.order.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T07：订单可用动作由服务端唯一计算，三端只渲染。
 */
@DisplayName("T07 订单可用动作")
class OrderActionsTest {

    @Test
    @DisplayName("待付款：可支付、可取消")
    void pendingPayment() {
        assertThat(OrderActions.of("PENDING_PAYMENT"))
                .containsExactly(OrderActions.PAY, OrderActions.CANCEL);
    }

    @Test
    @DisplayName("已付款：不可重复支付（无订单级写动作）")
    void paidHasNoPayAction() {
        assertThat(OrderActions.of("PAID")).isEmpty();
    }

    @Test
    @DisplayName("已发货：可确认收货")
    void shipped() {
        assertThat(OrderActions.of("SHIPPED")).containsExactly(OrderActions.CONFIRM_RECEIPT);
    }

    @Test
    @DisplayName("已完成：可评价")
    void completed() {
        assertThat(OrderActions.of("COMPLETED")).containsExactly(OrderActions.REVIEW);
    }

    @Test
    @DisplayName("终态与未知状态：无动作")
    void terminalStates() {
        assertThat(OrderActions.of("CANCELLED")).isEmpty();
        assertThat(OrderActions.of("REFUNDING")).isEmpty();
        assertThat(OrderActions.of("REFUNDED")).isEmpty();
        assertThat(OrderActions.of(null)).isEmpty();
        assertThat(OrderActions.of("UNKNOWN")).isEmpty();
    }
}
