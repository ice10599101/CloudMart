package com.cloudmart.order.mq;

import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * T10/C20 成团订单消费者测试：稳定成员订单键（重复消息幂等）、v2 信封强制（旧形状拒绝）、
 * 地址只认参团快照（不回当前默认地址）、失败分类处理（业务失败留痕 / 系统异常重抛）。
 */
class GroupSuccessOrderConsumerTest {

    private OrderService orderService;
    private OutboxService outboxService;
    private GroupSuccessOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        outboxService = mock(OutboxService.class);
        consumer = new GroupSuccessOrderConsumer(orderService, outboxService);
    }

    private Map<String, Object> member(long userId) {
        Map<String, Object> m = new HashMap<>();
        m.put("userId", userId);
        m.put("receiverName", "张三");
        m.put("receiverPhone", "13800138000");
        m.put("receiverAddress", "测试省测试市测试区测试路1号");
        m.put("orderTaskStatus", null);
        return m;
    }

    private Map<String, Object> message(String eventId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupOrderId", 3001L);
        payload.put("activityId", 2001L);
        payload.put("productId", 4001L);
        payload.put("skuId", 5001L);
        payload.put("groupPrice", "9.90");
        payload.put("memberUserIds", List.of(1001L, 1002L, 1003L));
        payload.put("members", List.of(member(1001L), member(1002L), member(1003L)));
        Map<String, Object> message = new HashMap<>();
        if (eventId != null) {
            message.put("eventId", eventId);
            message.put("eventType", "GROUP_SUCCESS");
            message.put("schemaVersion", 1);
        }
        message.put("payload", payload);
        return message;
    }

    @Test
    @DisplayName("T10：requestId = group-{id}-{userId} 稳定成员订单键贯穿建单；收货人取参团快照")
    void onMessage_success_stableMemberRequestKey() {
        OrderDTO order = mock(OrderDTO.class);
        when(order.id()).thenReturn(900L);
        when(orderService.createOrder(any(), any(CreateOrderRequest.class))).thenReturn(order);

        consumer.onMessage(message("evt-1"));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService, times(3)).createOrder(any(), captor.capture());
        List<CreateOrderRequest> sent = captor.getAllValues();
        assertThat(sent.get(0).requestId()).isEqualTo("group-3001-1001");
        assertThat(sent.get(1).requestId()).isEqualTo("group-3001-1002");
        assertThat(sent.get(2).requestId()).isEqualTo("group-3001-1003");
        assertThat(sent.get(0).groupOrderId()).isEqualTo(3001L);
        assertThat(sent.get(0).activityId()).isEqualTo(2001L);
        // C20：系统单收货人来自参团地址快照（不再消费时查默认地址）
        assertThat(sent.get(0).receiverName()).isEqualTo("张三");
        assertThat(sent.get(0).receiverAddress()).isEqualTo("测试省测试市测试区测试路1号");
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("C20：旧形状消息（无 eventId/schemaVersion）抛错拒绝（交 MQ 重试/死信，不再静默 ACK）")
    void onMessage_missingEventId_rejected() {
        Map<String, Object> message = message(null);

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("旧形状");
        verify(orderService, never()).createOrder(any(), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("C20：memberUserIds 有成员但 members 缺该成员快照 → 整条拒绝（不偷偷用默认地址）")
    void onMessage_missingMemberSnapshot_rejected() {
        Map<String, Object> message = message("evt-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        payload.put("members", List.of(member(1001L), member(1002L)));

        assertThatThrownBy(() -> consumer.onMessage(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("成员地址快照");
    }

    @Test
    @DisplayName("T11：成员快照标记 ABSENT_ADDRESS → 补充地址失败事件，不建单，继续其余成员")
    void onMessage_absentAddress_recordsFailedEvent() {
        OrderDTO order = mock(OrderDTO.class);
        when(order.id()).thenReturn(900L);
        Map<String, Object> absent = member(1002L);
        absent.put("orderTaskStatus", "ABSENT_ADDRESS");
        absent.put("receiverAddress", null);
        Map<String, Object> message = message("evt-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        payload.put("members", List.of(member(1001L), absent, member(1003L)));
        when(orderService.createOrder(eq(1001L), any(CreateOrderRequest.class))).thenReturn(order);
        when(orderService.createOrder(eq(1003L), any(CreateOrderRequest.class))).thenReturn(order);

        consumer.onMessage(message);

        verify(orderService, never()).createOrder(eq(1002L), any(CreateOrderRequest.class));
        verify(orderService, times(2)).createOrder(any(), any(CreateOrderRequest.class));
        verify(outboxService).record(argThat(e -> "GROUP_ORDER_FAILED".equals(e.eventType())
                && "group-order-failed-3001-1002".equals(e.eventId())));
    }

    @Test
    @DisplayName("业务失败（部分成员）→ 登记失败事件继续其余成员，稳定 eventId")
    void onMessage_partialBusinessFailure_recordsFailedEvents() {
        OrderDTO order = mock(OrderDTO.class);
        when(order.id()).thenReturn(900L);
        when(orderService.createOrder(eq(1002L), any(CreateOrderRequest.class)))
                .thenThrow(new BusinessException("STOCK_INSUFFICIENT", "商品库存不足"));
        when(orderService.createOrder(eq(1001L), any(CreateOrderRequest.class))).thenReturn(order);
        when(orderService.createOrder(eq(1003L), any(CreateOrderRequest.class))).thenReturn(order);

        consumer.onMessage(message("evt-1"));

        ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        com.cloudmart.common.async.EventEnvelope envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo("GROUP_ORDER_FAILED");
        assertThat(envelope.eventId()).isEqualTo("group-order-failed-3001-1002");
        assertThat(envelope.payload()).contains("\"userId\":1002");
    }

    @Test
    @DisplayName("重复成团消息（DUPLICATE_REQUEST）→ 幂等吸收不登记失败事件")
    void onMessage_duplicateRequest_idempotentlyAbsorbed() {
        OrderDTO order = mock(OrderDTO.class);
        when(order.id()).thenReturn(900L);
        when(orderService.createOrder(eq(1001L), any(CreateOrderRequest.class))).thenReturn(order);
        when(orderService.createOrder(eq(1002L), any(CreateOrderRequest.class)))
                .thenThrow(new BusinessException("DUPLICATE_REQUEST", "重复下单请求"));
        when(orderService.createOrder(eq(1003L), any(CreateOrderRequest.class))).thenReturn(order);

        consumer.onMessage(message("evt-1"));
        consumer.onMessage(message("evt-1"));

        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("系统异常 → 重抛（MQ 重试→死信），不吞错")
    void onMessage_systemError_rethrows() {
        when(orderService.createOrder(eq(1001L), any(CreateOrderRequest.class)))
                .thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> consumer.onMessage(message("evt-1")))
                .isInstanceOf(RuntimeException.class);

        verify(outboxService, never()).record(any());
    }
}
