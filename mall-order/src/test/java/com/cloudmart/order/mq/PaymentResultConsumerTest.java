package com.cloudmart.order.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C20：支付结果消费端形状判定——只接受 v2 信封 + 嵌套 payload，旧顶层形状一律拒绝。
 */
@DisplayName("PaymentResultConsumer 严格事件形状")
class PaymentResultConsumerTest {

    private Map<String, Object> envelope(Object payloadFields) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-1");
        message.put("eventType", "PAYMENT_SUCCEEDED");
        message.put("schemaVersion", 2);
        message.put("aggregateId", "9223372036854700002");
        message.put("requestId", "req-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadFields;
        message.put("payload", payload);
        return message;
    }

    @Test
    @DisplayName("完整信封：解析出信封头与强类型支付载荷")
    void envelopeShape_parsed() {
        Map<String, Object> message = envelope(Map.of(
                "orderId", 9001L, "paymentId", 8001L,
                "merchantPaymentNo", "MOCK08001", "amount", "10.00", "currency", "CNY"));

        var envelope = OrderEventMessages.requireEnvelope(message);
        PaymentSucceededPayload payload = OrderEventMessages.payload(message, PaymentSucceededPayload.class);

        assertThat(envelope.eventId()).isEqualTo("evt-1");
        assertThat(envelope.eventType()).isEqualTo("PAYMENT_SUCCEEDED");
        assertThat(envelope.schemaVersion()).isEqualTo(2);
        assertThat(payload.orderId()).isEqualTo(9001L);
        assertThat(payload.merchantPaymentNo()).isEqualTo("MOCK08001");
        assertThat(payload.amount()).isEqualTo("10.00");
    }

    @Test
    @DisplayName("C20：旧形状（orderId/event 在顶层、无 payload/schemaVersion）显式拒绝")
    void legacyShape_rejected() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", 9002L);
        message.put("event", "PAYMENT_SUCCESS");

        assertThatThrownBy(() -> OrderEventMessages.requireEnvelope(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("旧形状");
        assertThatThrownBy(() -> OrderEventMessages.requirePayload(message))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("缺 schemaVersion 的在途消息同样拒绝（不默认 v1）")
    void missingSchemaVersion_rejected() {
        Map<String, Object> message = envelope(Map.of("orderId", 9001L));
        message.remove("schemaVersion");

        assertThatThrownBy(() -> OrderEventMessages.requireEnvelope(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    @DisplayName("payload 缺 orderId 时强类型转换结果为空字段（由 onMessage 拒绝，不 NPE）")
    void payloadMissingOrderId_convertsToNullField() {
        Map<String, Object> message = envelope(Map.of("paymentId", 8001L));

        PaymentSucceededPayload payload = OrderEventMessages.payload(message, PaymentSucceededPayload.class);

        assertThat(payload.orderId()).isNull();
        assertThat(payload.paymentId()).isEqualTo(8001L);
    }
}
