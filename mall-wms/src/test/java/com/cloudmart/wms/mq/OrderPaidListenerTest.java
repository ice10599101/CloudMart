package com.cloudmart.wms.mq;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ASYNC-01 断点 3：WMS 拣货监听的消息形状判定——
 * 新 ORDER_PAID 事件信封（orderId 在 payload）与旧格式（顶层 orderId）均可解析。
 */
@DisplayName("OrderPaidListener 消息形状判定")
class OrderPaidListenerTest {

    @Test
    @DisplayName("新事件信封：从 payload 提取 orderId")
    void envelopeShape_parsed() {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-2");
        message.put("eventType", "ORDER_PAID");
        message.put("payload", Map.of("orderId", 9001L, "userId", 42L));

        assertThat(OrderPaidListener.extractOrderId(message)).isEqualTo(9001L);
        assertThat(OrderPaidListener.extractWarehouseId(message)).isEqualTo(1L);
    }

    @Test
    @DisplayName("旧格式：orderId/warehouseId 在顶层，仍可解析")
    void legacyShape_parsed() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", 9002L);
        message.put("warehouseId", 3L);

        assertThat(OrderPaidListener.extractOrderId(message)).isEqualTo(9002L);
        assertThat(OrderPaidListener.extractWarehouseId(message)).isEqualTo(3L);
    }

    @Test
    @DisplayName("形状未命中时显式失败（触发 MQ 重试而非 NPE 吞消息）")
    void unknownShape_failsExplicitly() {
        assertThatThrownBy(() -> OrderPaidListener.extractOrderId(new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 orderId");
    }
}
