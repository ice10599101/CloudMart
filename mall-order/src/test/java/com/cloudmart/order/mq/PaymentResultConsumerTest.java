package com.cloudmart.order.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ASYNC-01 断点 2：支付结果消费端形状判定——
 * 新事件信封（orderId 在 payload、类型在 eventType，v2 名 PAYMENT_SUCCEEDED）
 * 与旧格式（orderId/event 在顶层）都能正确解析。
 */
@DisplayName("PaymentResultConsumer 消息形状判定")
class PaymentResultConsumerTest {

    @Test
    @DisplayName("新事件信封：从 payload 提取 orderId， eventType 为 PAYMENT_SUCCEEDED")
    void envelopeShape_parsed() {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-1");
        message.put("eventType", "PAYMENT_SUCCEEDED");
        message.put("schemaVersion", 2);
        message.put("aggregateId", "9223372036854700002");
        message.put("requestId", "req-1");
        message.put("payload", Map.of("orderId", 9001L, "paymentId", 8001L));

        assertThat(PaymentResultConsumer.extractOrderId(message)).isEqualTo(9001L);
        assertThat(PaymentResultConsumer.extractEventType(message)).isEqualTo("PAYMENT_SUCCEEDED");
    }

    @Test
    @DisplayName("旧格式：orderId/event 在顶层，仍可解析（迁移期兼容）")
    void legacyShape_parsed() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", 9002L);
        message.put("event", "PAYMENT_SUCCESS");

        assertThat(PaymentResultConsumer.extractOrderId(message)).isEqualTo(9002L);
        assertThat(PaymentResultConsumer.extractEventType(message)).isEqualTo("PAYMENT_SUCCESS");
    }

    @Test
    @DisplayName("两种形状均未命中 orderId 时显式失败（不 NPE、不静默吞掉）")
    void unknownShape_failsExplicitly() {
        Map<String, Object> message = new HashMap<>();
        message.put("foo", "bar");

        assertThatThrownBy(() -> PaymentResultConsumer.extractOrderId(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 orderId");
    }
}
