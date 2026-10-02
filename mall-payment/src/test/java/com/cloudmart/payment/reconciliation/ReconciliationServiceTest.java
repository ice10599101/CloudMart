package com.cloudmart.payment.reconciliation;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.PaymentAttempt;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentAttemptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OPS-01/T01：对账核对迁移到 payment_attempt 台账——
 * SUCCESS 支付的订单未 PAID 记差异（HIGH）、PAID 订单无 SUCCESS 支付记差异、
 * 正常一致无差异、订单服务不可达不误报、差异唯一不重复。
 */
@DisplayName("ReconciliationService 对账核对")
class ReconciliationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 9001L;

    private ReconciliationService service;
    private PaymentAttemptMapper attemptMapper;
    private ReconciliationRunMapper runMapper;
    private ReconciliationDifferenceMapper differenceMapper;
    private OrderFeignClient orderFeignClient;

    @BeforeEach
    void setUp() {
        attemptMapper = mock(PaymentAttemptMapper.class);
        runMapper = mock(ReconciliationRunMapper.class);
        differenceMapper = mock(ReconciliationDifferenceMapper.class);
        orderFeignClient = mock(OrderFeignClient.class);
        service = new ReconciliationService(attemptMapper, runMapper, differenceMapper,
                orderFeignClient,
                org.mockito.Mockito.mock(com.cloudmart.payment.repository.RefundOrderMapper.class),
                org.mockito.Mockito.mock(com.cloudmart.payment.feign.InventoryReconFeignClient.class), 200);
    }

    private PaymentAttempt successAttempt(Long attemptId, Long orderId) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setId(attemptId);
        attempt.setOrderId(orderId);
        attempt.setStatus("SUCCESS");
        attempt.setAmount(new BigDecimal("88.00"));
        attempt.setCurrency("CNY");
        return attempt;
    }

    @Test
    @DisplayName("支付 SUCCESS 但订单未 PAID → 记 HIGH 差异")
    void reconcile_paymentSuccessOrderNotPaid_recordsDiff() {
        when(attemptMapper.selectList(any())).thenReturn(
                List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(List.of(), 0)));
        when(differenceMapper.selectCount(any())).thenReturn(0L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalChecked()).isEqualTo(1);
        assertThat(run.getTotalDiff()).isEqualTo(1);
        ArgumentCaptor<ReconciliationDifference> captor =
                ArgumentCaptor.forClass(ReconciliationDifference.class);
        verify(differenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiffType()).isEqualTo("PAYMENT_SUCCESS_ORDER_NOT_PAID");
        assertThat(captor.getValue().getSeverity()).isEqualTo("HIGH");
    }

    @Test
    @DisplayName("订单 PAID 但无 SUCCESS 支付 → 记 HIGH 差异")
    void reconcile_orderPaidNoSuccessPayment_recordsDiff() {
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(
                        List.of(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))), 1)));
        when(attemptMapper.selectCount(any())).thenReturn(0L);
        when(differenceMapper.selectCount(any())).thenReturn(0L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getTotalDiff()).isEqualTo(1);
        ArgumentCaptor<ReconciliationDifference> captor =
                ArgumentCaptor.forClass(ReconciliationDifference.class);
        verify(differenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiffType()).isEqualTo("ORDER_PAID_NO_SUCCESS_PAYMENT");
    }

    @Test
    @DisplayName("支付与订单状态一致 → 无差异")
    void reconcile_consistent_noDiff() {
        when(attemptMapper.selectList(any())).thenReturn(
                List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(
                        List.of(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))), 1)));
        when(attemptMapper.selectCount(any())).thenReturn(1L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getTotalDiff()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("订单服务不可达：核对一跳过不误报（无差异插入）")
    void reconcile_orderServiceDown_noFalsePositive() {
        when(attemptMapper.selectList(any())).thenReturn(
                List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenThrow(
                new RuntimeException("connection refused"));

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalDiff()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("差异唯一：重复 biz_id 不产生重复差异")
    void reconcile_duplicateDiff_suppressed() {
        when(attemptMapper.selectList(any())).thenReturn(
                List.of(successAttempt(1L, ORDER_ID), successAttempt(2L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(List.of(), 0)));
        when(differenceMapper.selectCount(any())).thenReturn(1L); // 已存在同键差异

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        // 两条支付命中同一差异键 → 均被抑制
        assertThat(run.getTotalDiff()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }
}
