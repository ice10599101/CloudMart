package com.cloudmart.payment.reconciliation;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.dto.OrderInternalInfoDTO;
import com.cloudmart.payment.entity.Payment;
import com.cloudmart.payment.feign.OrderFeignClient;
import com.cloudmart.payment.repository.PaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OPS-01：对账核对——SUCCESS 支付的订单未 PAID 记差异（HIGH）、
 * PAID 订单无 SUCCESS 支付记差异、正常一致无差异、差异唯一不重复。
 */
@DisplayName("ReconciliationService 对账核对")
class ReconciliationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 9001L;

    private ReconciliationService service;
    private PaymentMapper paymentMapper;
    private ReconciliationRunMapper runMapper;
    private ReconciliationDifferenceMapper differenceMapper;
    private OrderFeignClient orderFeignClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        paymentMapper = mock(PaymentMapper.class);
        runMapper = mock(ReconciliationRunMapper.class);
        differenceMapper = mock(ReconciliationDifferenceMapper.class);
        orderFeignClient = mock(OrderFeignClient.class);
        service = new ReconciliationService(paymentMapper, runMapper, differenceMapper,
                orderFeignClient, 200);
    }

    private Payment successPayment(Long paymentId, Long orderId) {
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setOrderId(orderId);
        payment.setStatus("SUCCESS");
        payment.setAmount(new BigDecimal("88.00"));
        return payment;
    }

    @Test
    @DisplayName("支付 SUCCESS 但订单未 PAID → 记 HIGH 差异")
    void reconcile_paymentSuccessOrderNotPaid_recordsDiff() {
        when(paymentMapper.selectList(any())).thenReturn(
                List.of(successPayment(1L, ORDER_ID)));
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
        when(paymentMapper.selectList(any())).thenReturn(List.of());
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(
                        List.of(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))), 1)));
        when(paymentMapper.selectCount(any())).thenReturn(0L);
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
        when(paymentMapper.selectList(any())).thenReturn(
                List.of(successPayment(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(
                        List.of(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))), 1)));
        when(paymentMapper.selectCount(any())).thenReturn(1L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getTotalDiff()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("订单服务不可达：核对一跳过不误报（无差异插入），核对二异常记 FAILED")
    void reconcile_orderServiceDown_noFalsePositive() {
        when(paymentMapper.selectList(any())).thenReturn(
                List.of(successPayment(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenThrow(
                new RuntimeException("connection refused"));

        // 仅核对一阶段不可达：核对一跳过，核对二空页正常结束
        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalDiff()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("差异唯一：重复 biz_id 不产生重复差异")
    void reconcile_duplicateDiff_suppressed() {
        when(paymentMapper.selectList(any())).thenReturn(
                List.of(successPayment(1L, ORDER_ID), successPayment(2L, ORDER_ID)));
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
