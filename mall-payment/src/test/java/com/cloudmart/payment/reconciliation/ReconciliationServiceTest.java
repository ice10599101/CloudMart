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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private com.cloudmart.payment.feign.InventoryReconFeignClient inventoryReconFeignClient;

    @BeforeEach
    void setUp() {
        attemptMapper = mock(PaymentAttemptMapper.class);
        runMapper = mock(ReconciliationRunMapper.class);
        differenceMapper = mock(ReconciliationDifferenceMapper.class);
        orderFeignClient = mock(OrderFeignClient.class);
        inventoryReconFeignClient = mock(com.cloudmart.payment.feign.InventoryReconFeignClient.class);
        service = new ReconciliationService(attemptMapper, runMapper, differenceMapper,
                orderFeignClient,
                org.mockito.Mockito.mock(com.cloudmart.payment.repository.RefundOrderMapper.class),
                inventoryReconFeignClient, 200);
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
        // T11：不可核验行计 skipped，与"核对一致"分开
        assertThat(run.getTotalSkipped()).isEqualTo(1);
        assertThat(run.getTotalChecked()).isZero();
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

    @Test
    @DisplayName("本业务日已 DONE → 幂等返回既有运行，不重扫")
    void reconcile_doneRun_idempotentReturn() {
        ReconciliationRun done = new ReconciliationRun();
        done.setId(7L);
        done.setBusinessDate(java.time.LocalDate.now());
        done.setScope("PAYMENT_ORDER");
        done.setStatus("DONE");
        when(runMapper.findByDateAndScope(any(), any())).thenReturn(done);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getId()).isEqualTo(7L);
        assertThat(run.getStatus()).isEqualTo("DONE");
        verify(attemptMapper, never()).selectList(any());
        verify(runMapper, never()).insert(any(ReconciliationRun.class));
    }

    @Test
    @DisplayName("本业务日上次 FAILED → CAS 认领重置重试（复用运行行并清理半程差异）")
    void reconcile_failedRun_claimedAndRetried() {
        ReconciliationRun failed = new ReconciliationRun();
        failed.setId(9L);
        failed.setBusinessDate(java.time.LocalDate.now());
        failed.setScope("PAYMENT_ORDER");
        failed.setStatus("FAILED");
        when(runMapper.findByDateAndScope(any(), any())).thenReturn(failed);
        when(runMapper.claimFailedRun(9L)).thenReturn(1);
        when(attemptMapper.selectList(any())).thenReturn(
                List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(List.of(), 0)));
        when(attemptMapper.selectCount(any())).thenReturn(1L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        // 复用 uk(business_date,scope) 约束下的同一运行行重扫并完成
        assertThat(run.getId()).isEqualTo(9L);
        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalDiff()).isZero();
        verify(runMapper).claimFailedRun(9L);
        verify(runMapper, never()).insert(any(ReconciliationRun.class));
        verify(differenceMapper).delete(any());
    }

    @Test
    @DisplayName("FAILED 运行被并发认领（CAS=0）→ 幂等返回不重扫")
    void reconcile_failedRunConcurrentlyClaimed_idempotentReturn() {
        ReconciliationRun failed = new ReconciliationRun();
        failed.setId(9L);
        failed.setBusinessDate(java.time.LocalDate.now());
        failed.setScope("PAYMENT_ORDER");
        failed.setStatus("FAILED");
        when(runMapper.findByDateAndScope(any(), any())).thenReturn(failed);
        when(runMapper.claimFailedRun(9L)).thenReturn(0);
        when(runMapper.selectById(9L)).thenReturn(failed);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getId()).isEqualTo(9L);
        verify(attemptMapper, never()).selectList(any());
        verify(differenceMapper, never()).delete(any());
    }

    @Test
    @DisplayName("库存台账扫描抛异常 → run 显式 FAILED 并上抛（不得静默记 0）")
    void inventoryScanUnavailable_failsRun() {
        when(inventoryReconFeignClient.scanReservations(any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> service.runInventoryReconciliation(7))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<ReconciliationRun> captor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("库存台账扫描返回失败信封 → run 显式 FAILED 并上抛")
    void inventoryScanErrorEnvelope_failsRun() {
        when(inventoryReconFeignClient.scanReservations(any(), anyLong(), anyInt())).thenReturn(
                ApiResponse.fail("INVENTORY_RECON_UNAVAILABLE", "库存预占台账暂不可用"));

        assertThatThrownBy(() -> service.runInventoryReconciliation(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INVENTORY_RECON_UNAVAILABLE");

        ArgumentCaptor<ReconciliationRun> captor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("台账 RESERVED 滞留超 24h 且订单已取消 → 记 HIGH 差异")
    void inventoryScan_stuckReservationOrderCancelled_recordsDiff() {
        when(inventoryReconFeignClient.scanReservations(any(), anyLong(), anyInt())).thenReturn(
                ApiResponse.ok(List.of(new com.cloudmart.payment.dto.ReservationScanDTO(
                        ORDER_ID, "RESERVED", 2, java.time.LocalDateTime.now().minusHours(48)))));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));
        when(differenceMapper.selectCount(any())).thenReturn(0L);

        ReconciliationRun run = service.runInventoryReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalChecked()).isEqualTo(1);
        assertThat(run.getTotalDiff()).isEqualTo(1);
        ArgumentCaptor<ReconciliationDifference> captor =
                ArgumentCaptor.forClass(ReconciliationDifference.class);
        verify(differenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiffType()).isEqualTo("RESERVATION_STUCK_ORDER_CLOSED");
    }
}
