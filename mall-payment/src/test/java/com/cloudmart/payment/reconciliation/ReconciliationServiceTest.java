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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OPS-01/T01/R26：对账核对 + 断点续扫/租约接管/PARTIAL 语义。
 */
@DisplayName("ReconciliationService 对账核对与 R26 可靠性")
class ReconciliationServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long ORDER_ID = 9001L;

    private ReconciliationService service;
    private PaymentAttemptMapper attemptMapper;
    private ReconciliationRunMapper runMapper;
    private ReconciliationDifferenceMapper differenceMapper;
    private OrderFeignClient orderFeignClient;
    private com.cloudmart.payment.repository.RefundOrderMapper refundOrderMapper;
    private com.cloudmart.payment.feign.InventoryReconFeignClient inventoryReconFeignClient;

    @BeforeEach
    void setUp() {
        attemptMapper = mock(PaymentAttemptMapper.class);
        runMapper = mock(ReconciliationRunMapper.class);
        differenceMapper = mock(ReconciliationDifferenceMapper.class);
        orderFeignClient = mock(OrderFeignClient.class);
        refundOrderMapper = mock(com.cloudmart.payment.repository.RefundOrderMapper.class);
        inventoryReconFeignClient = mock(com.cloudmart.payment.feign.InventoryReconFeignClient.class);
        service = new ReconciliationService(attemptMapper, runMapper, differenceMapper,
                orderFeignClient, refundOrderMapper, inventoryReconFeignClient, 200, 300);
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

    private ApiResponse<OrderFeignClient.PageDTO> emptyPaidPage() {
        return ApiResponse.ok(new OrderFeignClient.PageDTO(List.of(), 0));
    }

    private ReconciliationRun existingRun(Long id, String status) {
        ReconciliationRun run = new ReconciliationRun();
        run.setId(id);
        run.setBusinessDate(java.time.LocalDate.now());
        run.setScope("PAYMENT_ORDER");
        run.setStatus(status);
        run.setTotalChecked(0);
        run.setTotalDiff(0);
        run.setTotalSkipped(0);
        run.setPaymentCursor(0L);
        run.setRefundCursor(0L);
        run.setInventoryCursor(0L);
        run.setOrderPage(0);
        return run;
    }

    @Test
    @DisplayName("支付 SUCCESS 但订单未 PAID → 记 HIGH 差异，DONE")
    void reconcile_paymentSuccessOrderNotPaid_recordsDiff() {
        when(attemptMapper.selectList(any())).thenReturn(List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(emptyPaidPage());
        when(differenceMapper.selectCount(any())).thenReturn(0L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalChecked()).isEqualTo(1);
        assertThat(run.getTotalDiff()).isEqualTo(1);
        ArgumentCaptor<ReconciliationDifference> captor =
                ArgumentCaptor.forClass(ReconciliationDifference.class);
        verify(differenceMapper).insert(captor.capture());
        assertThat(captor.getValue().getDiffType()).isEqualTo("PAYMENT_SUCCESS_ORDER_NOT_PAID");
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
        verify(differenceMapper).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("支付与订单状态一致 → 无差异 DONE")
    void reconcile_consistent_noDiff() {
        when(attemptMapper.selectList(any())).thenReturn(List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(
                        List.of(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))), 1)));
        when(attemptMapper.selectCount(any())).thenReturn(1L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getTotalDiff()).isZero();
        assertThat(run.getStatus()).isEqualTo("DONE");
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("单笔订单查询不可达：skipped 不误报，整页可用仍 DONE")
    void reconcile_orderServiceDown_singleRowSkipped() {
        when(attemptMapper.selectList(any())).thenReturn(List.of(successAttempt(1L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenThrow(new RuntimeException("connection refused"));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(emptyPaidPage());

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalSkipped()).isEqualTo(1);
        assertThat(run.getTotalChecked()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("R11：零元 PAID 订单（zeroPay）无渠道支付凭证不报错（凭证是订单域 ZERO_PAY 事实）")
    void reconcile_zeroPayOrder_noFalseDiff() {
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(
                        List.of(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID",
                                java.math.BigDecimal.ZERO, true)), 1)));
        // 零元单确实没有 SUCCESS 支付尝试——但不应记差异
        when(attemptMapper.selectCount(any())).thenReturn(0L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalDiff()).isZero();
        assertThat(run.getTotalChecked()).isEqualTo(1);
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("差异唯一：重复 biz_id 不产生重复差异（断点续扫去重基础）")
    void reconcile_duplicateDiff_suppressed() {
        when(attemptMapper.selectList(any())).thenReturn(
                List.of(successAttempt(1L, ORDER_ID), successAttempt(2L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(emptyPaidPage());
        when(differenceMapper.selectCount(any())).thenReturn(1L);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getTotalDiff()).isZero();
        verify(differenceMapper, never()).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("本业务日已 DONE → 幂等返回，不重扫")
    void reconcile_doneRun_idempotentReturn() {
        when(runMapper.findByDateAndScope(any(), anyString())).thenReturn(existingRun(7L, "DONE"));

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getId()).isEqualTo(7L);
        verify(attemptMapper, never()).selectList(any());
        verify(runMapper, never()).insert(any(ReconciliationRun.class));
    }

    @Test
    @DisplayName("RUNNING 租约仍有效 → 幂等返回，防调度双跑")
    void reconcile_runningFreshLease_idempotentReturn() {
        ReconciliationRun running = existingRun(8L, "RUNNING");
        running.setLeaseOwner("other-instance");
        running.setLeaseUntil(LocalDateTime.now().plusMinutes(3));
        when(runMapper.findByDateAndScope(any(), anyString())).thenReturn(running);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getId()).isEqualTo(8L);
        verify(runMapper, never()).takeoverStaleRun(anyLong(), anyString(), anyInt());
        verify(attemptMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("RUNNING 租约过期（进程 kill）→ CAS 接管并从断点续扫")
    void reconcile_staleRunning_takenOverAndResumed() {
        ReconciliationRun stale = existingRun(8L, "RUNNING");
        stale.setLeaseOwner("dead-instance");
        stale.setLeaseUntil(LocalDateTime.now().minusMinutes(10));
        stale.setPaymentCursor(55L);
        stale.setTotalChecked(3);
        when(runMapper.findByDateAndScope(any(), anyString())).thenReturn(stale);
        when(runMapper.takeoverStaleRun(eq(8L), anyString(), eq(300))).thenReturn(1);
        when(runMapper.selectById(8L)).thenReturn(stale);
        // 55 之前的游标已处理：首批直接返回空（模拟全部扫描完）
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(emptyPaidPage());

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        verify(runMapper).takeoverStaleRun(eq(8L), anyString(), eq(300));
        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getPaymentCursor()).isEqualTo(55L);
        // 续跑不清零已核对数
        assertThat(run.getTotalChecked()).isEqualTo(3);
        verify(differenceMapper, never()).delete(any());
    }

    @Test
    @DisplayName("接管竞争失败（CAS=0）→ 幂等返回不重扫")
    void reconcile_staleRunning_takeoverLost() {
        ReconciliationRun stale = existingRun(8L, "RUNNING");
        stale.setLeaseUntil(LocalDateTime.now().minusMinutes(10));
        when(runMapper.findByDateAndScope(any(), anyString())).thenReturn(stale);
        when(runMapper.takeoverStaleRun(anyLong(), anyString(), anyInt())).thenReturn(0);
        when(runMapper.selectById(8L)).thenReturn(stale);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getId()).isEqualTo(8L);
        verify(attemptMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("FAILED/PARTIAL 认领续跑：不删差异、不清零、从断点继续")
    void reconcile_partialRun_claimedAndResumedWithoutDeletingEvidence() {
        ReconciliationRun partial = existingRun(9L, "PARTIAL");
        partial.setOrderPage(1);
        partial.setTotalChecked(1);
        when(runMapper.findByDateAndScope(any(), anyString())).thenReturn(partial);
        when(runMapper.claimResumableRun(eq(9L), anyString(), eq(300))).thenReturn(1);
        when(runMapper.selectById(9L)).thenReturn(partial);
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        // 断点在第 1 页之后：只应请求第 2 页，第 2 页真空结束
        when(orderFeignClient.listPaidOrders(2, 200)).thenReturn(emptyPaidPage());

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getOrderPage()).isEqualTo(1);
        assertThat(run.getTotalChecked()).isEqualTo(1);
        verify(orderFeignClient, never()).listPaidOrders(eq(1), anyInt());
        verify(differenceMapper, never()).delete(any());
    }

    @Test
    @DisplayName("反向扫描第 2 页依赖失败：PARTIAL 保留断点（绝不能 DONE）")
    void reconcile_paidOrderPage2Unreachable_partialWithCursor() {
        // 第 1 页满页（200）才会继续翻第 2 页；第 2 页失败信封→PARTIAL
        OrderInternalInfoDTO paid = new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"));
        List<OrderInternalInfoDTO> fullPage = java.util.Collections.nCopies(200, paid);
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(fullPage, 201)));
        when(attemptMapper.selectCount(any())).thenReturn(1L);
        when(orderFeignClient.listPaidOrders(2, 200)).thenReturn(null);

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        assertThat(run.getFinishedAt()).isNull();
        assertThat(run.getOrderPage()).isEqualTo(1);
        assertThat(run.getTotalChecked()).isEqualTo(200);
    }

    @Test
    @DisplayName("反向扫描第 2 页抛异常：同样 PARTIAL 且心跳/租约已刷新")
    void reconcile_paidOrderPage2Throws_partial() {
        // 满页后第 2 页网络异常：PARTIAL 保留断点
        OrderInternalInfoDTO paid = new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"));
        List<OrderInternalInfoDTO> fullPage = java.util.Collections.nCopies(200, paid);
        when(attemptMapper.selectList(any())).thenReturn(List.of());
        when(orderFeignClient.listPaidOrders(1, 200)).thenReturn(
                ApiResponse.ok(new OrderFeignClient.PageDTO(fullPage, 200)));
        when(attemptMapper.selectCount(any())).thenReturn(1L);
        when(orderFeignClient.listPaidOrders(2, 200)).thenThrow(new RuntimeException("read timeout"));

        ReconciliationRun run = service.runPaymentOrderReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        assertThat(run.getOrderPage()).isEqualTo(1);
        assertThat(run.getLeaseUntil()).isNotNull();
        assertThat(run.getHeartbeatAt()).isNotNull();
    }

    @Test
    @DisplayName("正向扫描每批持久化断点游标（kill 后从游标恢复的前提）")
    void reconcile_paymentCursor_persistedPerBatch() {
        when(attemptMapper.selectList(any())).thenReturn(List.of(
                successAttempt(10L, ORDER_ID), successAttempt(20L, ORDER_ID)));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "PAID", new BigDecimal("88.00"))));
        when(orderFeignClient.listPaidOrders(anyInt(), anyInt())).thenReturn(emptyPaidPage());
        when(attemptMapper.selectCount(any())).thenReturn(1L);

        service.runPaymentOrderReconciliation(7);

        ArgumentCaptor<ReconciliationRun> captor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getAllValues()).anySatisfy(r ->
                assertThat(r.getPaymentCursor()).isEqualTo(20L));
    }

    @Test
    @DisplayName("库存台账扫描抛异常 → PARTIAL 保留断点（不再静默 FAILED 0 结果）")
    void inventoryScanUnreachable_partial() {
        when(inventoryReconFeignClient.scanReservations(any(), anyLong(), anyInt()))
                .thenThrow(new RuntimeException("connection refused"));

        ReconciliationRun run = service.runInventoryReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        assertThat(run.getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("库存台账返回失败信封 → PARTIAL")
    void inventoryScanErrorEnvelope_partial() {
        when(inventoryReconFeignClient.scanReservations(any(), anyLong(), anyInt())).thenReturn(
                ApiResponse.fail("INVENTORY_RECON_UNAVAILABLE", "库存预占台账暂不可用"));

        ReconciliationRun run = service.runInventoryReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("库存扫描正常且命中差异 → DONE 记差异")
    void inventoryScan_stuckReservationOrderCancelled_recordsDiff() {
        when(inventoryReconFeignClient.scanReservations(any(), anyLong(), anyInt())).thenReturn(
                ApiResponse.ok(List.of(new com.cloudmart.payment.dto.ReservationScanDTO(
                        ORDER_ID, "RESERVED", 2, LocalDateTime.now().minusHours(48)))));
        when(orderFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                ApiResponse.ok(new OrderInternalInfoDTO(ORDER_ID, USER_ID, "CANCELLED", new BigDecimal("88.00"))));
        when(differenceMapper.selectCount(any())).thenReturn(0L);

        ReconciliationRun run = service.runInventoryReconciliation(7);

        assertThat(run.getStatus()).isEqualTo("DONE");
        assertThat(run.getTotalDiff()).isEqualTo(1);
        assertThat(run.getInventoryCursor()).isEqualTo(ORDER_ID);
        verify(differenceMapper).insert(any(ReconciliationDifference.class));
    }

    @Test
    @DisplayName("意外异常（非依赖不可达）→ FAILED 并上抛")
    void unexpectedException_marksFailedAndRethrows() {
        when(attemptMapper.selectList(any())).thenThrow(new RuntimeException("db connection lost"));

        assertThatThrownBy(() -> service.runPaymentOrderReconciliation(7))
                .isInstanceOf(IllegalStateException.class);

        ArgumentCaptor<ReconciliationRun> captor = ArgumentCaptor.forClass(ReconciliationRun.class);
        verify(runMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
    }
}
