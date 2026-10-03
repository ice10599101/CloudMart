package com.cloudmart.wms.mq;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.repository.WarehouseMapper;
import com.cloudmart.wms.service.InboundOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T11 切片三：售后退货入库监听——形状判定、RETURN_REFUND 建单、
 * REFUND_ONLY 跳过、幂等跳过、SKU 缺失显式失败。
 */
@DisplayName("AfterSaleReturnListener 退货入库联动")
class AfterSaleReturnListenerTest {

    private InboundOrderService inboundOrderService;
    private WarehouseMapper warehouseMapper;
    private AfterSaleReturnListener listener;

    @BeforeEach
    void setUp() {
        inboundOrderService = mock(InboundOrderService.class);
        warehouseMapper = mock(WarehouseMapper.class);
        listener = new AfterSaleReturnListener(inboundOrderService, warehouseMapper);
    }

    private Map<String, Object> envelope(String eventType, Map<String, Object> payload) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-as-1");
        message.put("eventType", eventType);
        message.put("payload", payload);
        return message;
    }

    private Map<String, Object> returnRefundPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caseId", 11L);
        payload.put("caseNo", "AS20261003001");
        payload.put("orderId", 9001L);
        payload.put("userId", 42L);
        payload.put("afterSaleType", "RETURN_REFUND");
        payload.put("itemId", 5L);
        payload.put("skuId", 301L);
        payload.put("productName", "无线鼠标");
        payload.put("quantity", 2);
        return payload;
    }

    @Test
    @DisplayName("RETURN_REFUND 质检通过 → 建 RETURN 入库单（仓库取第一个可用）")
    void returnRefund_createsInbound() {
        when(inboundOrderService.findByTypeAndReferenceNo("RETURN", "AS20261003001")).thenReturn(null);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(7L);
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(warehouse));

        listener.onMessage(envelope(AfterSaleReturnListener.EVENT_TYPE, returnRefundPayload()));

        verify(inboundOrderService).createInboundOrder(argThat((CreateInboundOrderRequest r) ->
                r.warehouseId().equals(7L)
                        && "RETURN".equals(r.type())
                        && "AS20261003001".equals(r.referenceNo())
                        && r.items().size() == 1
                        && r.items().get(0).skuId().equals(301L)
                        && "无线鼠标".equals(r.items().get(0).productName())
                        && r.items().get(0).expectedQuantity().equals(2)));
    }

    @Test
    @DisplayName("REFUND_ONLY 仅退款无货退回 → 跳过不建单")
    void refundOnly_skipped() {
        Map<String, Object> payload = returnRefundPayload();
        payload.put("afterSaleType", "REFUND_ONLY");

        listener.onMessage(envelope(AfterSaleReturnListener.EVENT_TYPE, payload));

        verify(inboundOrderService, never()).createInboundOrder(any());
    }

    @Test
    @DisplayName("退货入库单已存在 → 幂等跳过")
    void existingInbound_idempotentSkip() {
        when(inboundOrderService.findByTypeAndReferenceNo(eq("RETURN"), eq("AS20261003001")))
                .thenReturn(new InboundOrderDTO(1L, 7L, "RETURN", "AS20261003001",
                        "PENDING", 2, 0, null, null, null, null, List.of()));

        listener.onMessage(envelope(AfterSaleReturnListener.EVENT_TYPE, returnRefundPayload()));

        verify(inboundOrderService, never()).createInboundOrder(any());
    }

    @Test
    @DisplayName("并发双投递触发唯一键 → DuplicateKey 视为已建，不重试")
    void duplicateKey_tolerated() {
        when(inboundOrderService.findByTypeAndReferenceNo("RETURN", "AS20261003001")).thenReturn(null);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(7L);
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(warehouse));
        when(inboundOrderService.createInboundOrder(any()))
                .thenThrow(new DuplicateKeyException("uk_inbound_orders_type_ref"));

        assertThatCode(() -> listener.onMessage(
                envelope(AfterSaleReturnListener.EVENT_TYPE, returnRefundPayload())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RETURN_REFUND 但缺 SKU 明细 → 显式抛出（重试进死信，不静默丢件）")
    void missingSku_fails() {
        Map<String, Object> payload = returnRefundPayload();
        payload.remove("skuId");
        when(inboundOrderService.findByTypeAndReferenceNo("RETURN", "AS20261003001")).thenReturn(null);

        assertThatThrownBy(() -> listener.onMessage(
                envelope(AfterSaleReturnListener.EVENT_TYPE, payload)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AS20261003001");
    }

    @Test
    @DisplayName("缺少 eventId（旧形状）或未知事件类型 → 拒绝消费不建单")
    void malformedEvents_rejected() {
        Map<String, Object> noEventId = new HashMap<>();
        noEventId.put("eventType", AfterSaleReturnListener.EVENT_TYPE);
        noEventId.put("payload", returnRefundPayload());
        listener.onMessage(noEventId);

        listener.onMessage(envelope("ORDER_PAID", returnRefundPayload()));

        verify(inboundOrderService, never()).createInboundOrder(any());
        assertThat(inboundOrderService).isNotNull();
    }
}
