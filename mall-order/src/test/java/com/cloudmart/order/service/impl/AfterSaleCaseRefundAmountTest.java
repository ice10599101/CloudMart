package com.cloudmart.order.service.impl;

import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.entity.AfterSaleCase;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.repository.AfterSaleCaseEventMapper;
import com.cloudmart.order.repository.AfterSaleCaseMapper;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T11 部分退款金额上限（P1 资金安全）：
 * ① 行级——退货退款批准金额不得超过订单项小计；
 * ② 订单级——同订单其他案件已占用退款 + 本案 ≤ 订单实付（防叠加超退）。
 */
@DisplayName("AfterSaleCaseServiceImpl 退款金额上限校验")
class AfterSaleCaseRefundAmountTest {

    private AfterSaleCaseMapper caseMapper;
    private AfterSaleCaseEventMapper eventMapper;
    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private AfterSaleCaseServiceImpl service;

    private AfterSaleCase caseEntity;
    private Order order;

    @BeforeEach
    void setUp() {
        caseMapper = mock(AfterSaleCaseMapper.class);
        eventMapper = mock(AfterSaleCaseEventMapper.class);
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        service = new AfterSaleCaseServiceImpl(caseMapper, eventMapper, orderMapper, orderItemMapper,
                mock(OutboxService.class), new ObjectMapper());

        caseEntity = new AfterSaleCase();
        caseEntity.setId(11L);
        caseEntity.setCaseNo("AS20261003001");
        caseEntity.setOrderId(9001L);
        caseEntity.setUserId(42L);

        order = new Order();
        order.setId(9001L);
        order.setOrderNo("NO9001");
        order.setPayAmount(new BigDecimal("1998.00"));
        when(orderMapper.selectById(9001L)).thenReturn(order);
    }

    @Test
    @DisplayName("退货退款批准金额超过订单项小计 → 拒绝（AFTER_SALE_AMOUNT_EXCEEDED）")
    void approve_exceedsItemSubtotal_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setQuantity(1);
        when(orderItemMapper.selectById(5L)).thenReturn(item);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("1500.00"), "RF9001"))
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

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("600.00"), "RF9001"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "AFTER_SALE_AMOUNT_EXCEEDED")
                .hasMessageContaining("1500.00");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }

    @Test
    @DisplayName("金额在行小计与订单实付余量内 → 正常受理")
    void approve_withinLimits_approved() {
        caseEntity.setType(AfterSaleCase.TYPE_RETURN_REFUND);
        caseEntity.setItemId(5L);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        OrderItem item = new OrderItem();
        item.setId(5L);
        item.setPrice(new BigDecimal("999.00"));
        item.setQuantity(2);
        when(orderItemMapper.selectById(5L)).thenReturn(item);
        when(caseMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.approve(eq(11L), any(), any(), eq(1L))).thenReturn(1);
        when(eventMapper.selectList(any())).thenReturn(List.of());
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);

        var vo = service.approve(1L, 11L, new BigDecimal("999.00"), "RF9001");

        assertThat(vo).isNotNull();
        verify(caseMapper).approve(eq(11L), eq(new BigDecimal("999.00")), eq("RF9001"), eq(1L));
    }

    @Test
    @DisplayName("订单缺失或实付金额为空 → 拒绝受理（fail-closed）")
    void approve_orderMissing_rejected() {
        caseEntity.setType(AfterSaleCase.TYPE_REFUND_ONLY);
        when(caseMapper.selectById(11L)).thenReturn(caseEntity);
        when(orderMapper.selectById(9001L)).thenReturn(null);

        assertThatThrownBy(() -> service.approve(1L, 11L, new BigDecimal("10.00"), "RF9001"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ORDER_NOT_FOUND");
        verify(caseMapper, never()).approve(any(), any(), any(), any());
    }
}
