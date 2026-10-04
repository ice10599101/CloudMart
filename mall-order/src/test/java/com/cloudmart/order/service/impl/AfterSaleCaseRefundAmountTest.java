package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.repository.AfterSaleCaseEventMapper;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T04 售后按明细 case 结算（资金安全）：
 * ① 行级——批准金额 ≤ 申请数量 × 明细实付单价（pay_amount 分摊优先，历史单回退 price）；
 * ② 订单级——订单行锁内其他案件占用（APPROVED+REFUNDED）+ 本案 ≤ 订单实付；
 * ③ 退款号服务端派生 RFC{caseId}，后台不再输入；④ REFUND_ONLY 审批同事务写
 * AFTER_SALE_REFUND_SUBMIT Outbox；⑤ 数量维度占用防重复申请同一件商品。
 */
@DisplayName("AfterSaleCaseServiceImpl T04 结算校验")
class AfterSaleCaseRefundAmountTest {

    private AfterSaleCaseMapper caseMapper;
    private AfterSaleCaseEventMapper eventMapper;
    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OutboxService outboxService;
    private AfterSaleCaseServiceImpl service;

    private AfterSaleCase caseEntity;
    private Order order;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{AfterSaleCase.class, OrderItem.class, Order.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        caseMapper = mock(AfterSaleCaseMapper.class);
        eventMapper = mock(AfterSaleCaseEventMapper.class);
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        outboxService = mock(OutboxService.class);
        service = new AfterSaleCaseServiceImpl(caseMapper, eventMapper, orderMapper, orderItemMapper,
                outboxService, new ObjectMapper());

        caseEntity = new AfterSaleCase();
        caseEntity.setId(11L);
        caseEntity.setCaseNo("AS20261003001");
        caseEntity.setOrderId(9001L);
        caseEntity.setUserId(42L);
        caseEntity.setStatus(AfterSaleCase.STATUS_PENDING);
        caseEntity.setQuantity(1);

        order = new Order();
        order.setId(9001L);
        order.setOrderNo("NO9001");
        order.setPayAmount(new BigDecimal("1998.00"));
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(order);
    }

    @Test
    @DisplayName("退货退款批准金额超过 行可退上限（数量×明细实付单价）→ 拒绝")
    void approve_exceedsItemCap_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        caseEntity.setQuantity(1);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setPayAmount(new BigDecimal("899.10"));
        item.setQuantity(1);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("999.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("同订单其他案件已占用退款，叠加本案超过订单实付 → 拒绝")
    void approve_cumulativeExceedsPayAmount_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        AfterSaleCase other = new AfterSaleCase();
        other.setId(12L);
        other.setStatus(AfterSaleCase.STATUS_REFUNDED);
        other.setRefundAmount(new BigDecimal("1500.00"));
        when(caseMapper.selectList(any())).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("600.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED")
                .hasMessageContaining("1500.00");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("金额在行上限与订单实付余量内 → 正常受理，退款号=RFC{caseId}，仅退款写提交 Outbox")
    void approve_withinLimits_approved() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        caseEntity.setQuantity(2);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setPayAmount(new BigDecimal("999.00"));
        item.setQuantity(2);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        var vo = service.approve(1L, 11L, new BigDecimal("999.00"));

        assertThat(vo).isNotNull();
        verify(caseMapper).approve(eq(11L), eq(new BigDecimal("999.00")), eq("RFC11"), eq(1L));
    }

    @Test
    @DisplayName("REFUND_ONLY 受理 → 同事务写 AFTER_SALE_REFUND_SUBMIT Outbox（案件事实驱动退款提交）")
    void approve_refundOnly_recordsSubmitEvent() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        // mock 不会回写 CAS 结果：受理后的重读行由桩提供（refundNo/amount 已落库）
        caseEntity.setRefundNo("RFC11");
        caseEntity.setRefundAmount(new BigDecimal("500.00"));
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L, 11L, new BigDecimal("500.00"));

        ArgumentCaptor<EventEnvelope> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("AFTER_SALE_REFUND_SUBMIT");
        assertThat(captor.getValue().payload()).contains("\"refundNo\":\"RFC11\"");
    }

    @Test
    @DisplayName("订单行锁参与额度串行化：approve 先锁订单（selectByIdForUpdate）")
    void approve_locksOrderRow() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        service.approve(1L, 11L, new BigDecimal("100.00"));

        verify(orderMapper).selectByIdForUpdate(9001L);
    }

    @Test
    @DisplayName("非 PENDING 案件受理 → AFTER_SALE_STATUS_ERROR")
    void approve_notPending_rejected() {
        caseEntity.setStatus(AfterSaleCase.STATUS_APPROVED);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_STATUS_ERROR");
        verify(orderMapper, never()).selectByIdForUpdate(anyLong());
    }

    @Test
    @DisplayName("订单缺失或实付金额为空 → 拒绝受理（fail-closed）")
    void approve_orderMissing_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(orderMapper.selectByIdForUpdate(9001L)).thenReturn(null);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("10.00")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("T04 数量占用：同明细已占用数量 + 本次 > 明细数量 → 拒绝申请")
    void apply_itemQuantityOccupied_rejected() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectById(9001L)).thenReturn(applyOrder);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setOrderId(9001L);
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(2);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        AfterSaleCase occupying = new AfterSaleCase();
        occupying.setId(12L);
        occupying.setItemId(5L);
        occupying.setQuantity(1);
        occupying.setStatus(AfterSaleCase.STATUS_REFUNDED);
        when(caseMapper.selectList(any())).thenReturn(List.of(occupying));

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, 5L, AfterSaleCase.TYPE_RETURN_REFUND, "破损", null, 2);

        assertThatThrownBy(() -> service.apply(42L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_QUANTITY_EXCEEDED");
        verify(caseMapper, never()).insert(any(AfterSaleCase.class));
    }

    @Test
    @DisplayName("T04 数量占用：同一订单项另一案件 PENDING 中，重复申请同项 → AFTER_SALE_DUPLICATE")
    void apply_pendingDuplicate_rejected() {
        Order applyOrder = new Order();
        applyOrder.setId(9001L);
        applyOrder.setUserId(42L);
        applyOrder.setStatus("PAID");
        when(orderMapper.selectById(9001L)).thenReturn(applyOrder);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setOrderId(9001L);
        item.setQuantity(2);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.selectCount(any())).thenReturn(1L);

        var request = new com.cloudmart.order.dto.CreateAfterSaleRequest(
                9001L, 5L, AfterSaleCase.TYPE_RETURN_REFUND, "破损", null, 1);

        assertThatThrownBy(() -> service.apply(42L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_DUPLICATE");
    }

    @Test
    @DisplayName("markRefundedReturning：CAS 命中返回案件（含金额），未命中幂等返回 null")
    void markRefundedReturning_idempotent() {
        when(caseMapper.markRefunded("RFC11")).thenReturn(1);
        caseEntity.setRefundNo("RFC11");
        caseEntity.setStatus(AfterSaleCase.STATUS_REFUNDED);
        caseEntity.setRefundAmount(new BigDecimal("100.00"));
        when(caseMapper.selectOne(any())).thenReturn(caseEntity);
        when(eventMapper.selectList(any())).thenReturn(List.of());

        var refunded = service.markRefundedReturning("RFC11");
        assertThat(refunded).isNotNull();
        assertThat(refunded.getRefundAmount()).isEqualByComparingTo("100.00");

        when(caseMapper.markRefunded("RFC11")).thenReturn(0);
        assertThat(service.markRefundedReturning("RFC11")).isNull();
    }

    @Test
    @DisplayName("退款号派生：RFC{caseId}（orders.id 为雪花，与 case 自增空间不重叠）")
    void refundNo_derivedFromCaseId() {
        assertThat(AfterSaleCaseServiceImpl.refundNoOf(11L)).isEqualTo("RFC11");
        assertThat(AfterSaleCaseServiceImpl.refundNoOf(123456789L)).isEqualTo("RFC123456789");
    }
}
