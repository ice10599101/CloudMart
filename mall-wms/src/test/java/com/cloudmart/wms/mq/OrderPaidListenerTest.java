package com.cloudmart.wms.mq;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.repository.WarehouseMapper;
import com.cloudmart.wms.service.PickOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C20：ORDER_PAID 监听只接受 v2 信封（orderId/warehouseId 在 payload），
 * 旧顶层形状拒绝；仓库分配取载荷指定或第一个可用仓库。
 */
@DisplayName("OrderPaidListener 严格事件形状与仓库分配")
class OrderPaidListenerTest {

    private WarehouseMapper warehouseMapper;
    private OrderPaidListener listener;

    @BeforeEach
    void setUp() {
        PickOrderService pickOrderService = mock(PickOrderService.class);
        warehouseMapper = mock(WarehouseMapper.class);
        listener = new OrderPaidListener(pickOrderService, warehouseMapper);
    }

    private Map<String, Object> envelope(Object payloadFields) {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-2");
        message.put("eventType", "ORDER_PAID");
        message.put("schemaVersion", 2);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadFields;
        message.put("payload", payload);
        return message;
    }

    @Test
    @DisplayName("v2 信封：payload 提取 orderId；仓库取第一个可用")
    void envelopeShape_parsed() {
        Map<String, Object> message = envelope(Map.of("orderId", 9001L, "userId", 42L));

        Warehouse warehouse = new Warehouse();
        warehouse.setId(7L);
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(warehouse));

        assertThat(OrderPaidListener.extractOrderId(message)).isEqualTo(9001L);
        assertThat(listener.extractWarehouseIdOrDefault(message)).isEqualTo(7L);
    }

    @Test
    @DisplayName("payload 指定 warehouseId 时优先使用载荷仓库")
    void payloadWarehouseId_used() {
        Map<String, Object> message = envelope(Map.of("orderId", 9001L, "warehouseId", 3L));

        assertThat(listener.extractWarehouseIdOrDefault(message)).isEqualTo(3L);
    }

    @Test
    @DisplayName("事件未指定仓库且无可用仓库 → 显式失败（事件重试）")
    void noWarehouseAvailable_failsExplicitly() {
        Map<String, Object> message = envelope(Map.of("orderId", 9001L));
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> listener.extractWarehouseIdOrDefault(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无可用仓库");
    }

    @Test
    @DisplayName("C20：旧格式（orderId/warehouseId 在顶层、无信封头）显式拒绝")
    void legacyShape_rejected() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", 9002L);
        message.put("warehouseId", 3L);

        assertThatThrownBy(() -> OrderPaidListener.requireEnvelope(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("旧形状");
        assertThatThrownBy(() -> OrderPaidListener.extractOrderId(message))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("有信封头但 payload 缺 orderId → 拒绝（不回落顶层字段）")
    void envelopeWithoutOrderId_rejected() {
        Map<String, Object> message = envelope(Map.of("userId", 42L));

        assertThatThrownBy(() -> OrderPaidListener.extractOrderId(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orderId");
    }

    @Test
    @DisplayName("空消息不通过信封校验（触发 MQ 重试而非 NPE 吞消息）")
    void emptyMessage_rejected() {
        assertThatCode(() -> OrderPaidListener.requireEnvelope(new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
