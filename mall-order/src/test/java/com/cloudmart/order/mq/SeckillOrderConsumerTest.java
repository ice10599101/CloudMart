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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T09 秒杀下单消费者测试：requestId 贯穿（订单 request_key 即 requestId），
 * 业务失败登记结果回写事件（终态），系统异常重抛触发 MQ 重试。
 */
class SeckillOrderConsumerTest {

    private OrderService orderService;
    private OutboxService outboxService;
    private SeckillOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        outboxService = mock(OutboxService.class);
        consumer = new SeckillOrderConsumer(orderService, outboxService);
    }

    private Map<String, Object> message(String requestId) {
        Map<String, Object> message = new HashMap<>();
        message.put("requestId", requestId);
        message.put("userId", 1001L);
        message.put("activityId", 2001L);
        message.put("seckillProductId", 3001L);
        message.put("skuId", 4001L);
        message.put("seckillPrice", "99.00");
        message.put("quantity", 1);
        return message;
    }

    @Test
    @DisplayName("T09：requestId 作为订单 request_key 与 seckillRequestId 贯穿建单")
    void onMessage_success_requestIdFlowsThrough() {
        OrderDTO order = mock(OrderDTO.class);
        org.mockito.Mockito.when(order.id()).thenReturn(555L);
        org.mockito.Mockito.when(orderService.createOrder(eq(1001L), any(CreateOrderRequest.class)))
                .thenReturn(order);

        consumer.onMessage(message("req-0001"));

        ArgumentCaptor<CreateOrderRequest> captor = ArgumentCaptor.forClass(CreateOrderRequest.class);
        verify(orderService).createOrder(eq(1001L), captor.capture());
        CreateOrderRequest sent = captor.getValue();
        assertThat(sent.requestId()).isEqualTo("req-0001");
        assertThat(sent.seckillRequestId()).isEqualTo("req-0001");
        assertThat(sent.activityId()).isEqualTo(2001L);
        // SUCCESS 结果事件由 createOrder 事务内登记，消费者不再发送
        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("T09：旧形状消息（无 requestId）拒绝消费")
    void onMessage_missingRequestId_rejected() {
        Map<String, Object> message = message(null);

        consumer.onMessage(message);

        verify(orderService, never()).createOrder(any(), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("业务失败 → 登记终态 FAILED 结果事件（稳定 eventId）后 ACK")
    void onMessage_businessFailure_recordsFailedEvent() {
        doThrow(new BusinessException("STOCK_INSUFFICIENT", "商品库存不足"))
                .when(orderService).createOrder(eq(1001L), any(CreateOrderRequest.class));

        consumer.onMessage(message("req-0002"));

        ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
        verify(outboxService).record(captor.capture());
        com.cloudmart.common.async.EventEnvelope envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo("SECKILL_RESULT");
        assertThat(envelope.eventId()).isEqualTo("seckill-result-req-0002");
        assertThat(envelope.requestId()).isEqualTo("req-0002");
        assertThat(envelope.payload()).contains("\"success\":false");
        assertThat(envelope.payload()).contains("商品库存不足");
    }

    @Test
    @DisplayName("系统异常 → 重抛（MQ 重试→死信），不登记失败事件不吞错")
    void onMessage_systemError_rethrows() {
        doThrow(new RuntimeException("db down"))
                .when(orderService).createOrder(eq(1001L), any(CreateOrderRequest.class));

        assertThatThrownBy(() -> consumer.onMessage(message("req-0003")))
                .isInstanceOf(RuntimeException.class);

        verify(outboxService, never()).record(any());
    }

    @Test
    @DisplayName("重复消费（消息重投）：createOrder 幂等重放，不再重复登记事件")
    void onMessage_redelivery_idempotentReplay() {
        OrderDTO order = mock(OrderDTO.class);
        org.mockito.Mockito.when(order.id()).thenReturn(555L);
        org.mockito.Mockito.when(orderService.createOrder(eq(1001L), any(CreateOrderRequest.class)))
                .thenReturn(order);

        // 100 次重复消息
        for (int i = 0; i < 100; i++) {
            consumer.onMessage(message("req-0001"));
        }

        verify(orderService, org.mockito.Mockito.times(100))
                .createOrder(eq(1001L), any(CreateOrderRequest.class));
        verify(outboxService, never()).record(any());
    }
}
