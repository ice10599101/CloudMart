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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ASYNC-01 断点 3 + WMS-01：WMS 拣货监听——消息形状判定（新信封/旧格式）与
 * 仓库分配（不再硬编码 1，取第一个可用仓库）。
 */
@DisplayName("OrderPaidListener 形状判定与仓库分配")
class OrderPaidListenerTest {

    private WarehouseMapper warehouseMapper;
    private OrderPaidListener listener;

    @BeforeEach
    void setUp() {
        PickOrderService pickOrderService = mock(PickOrderService.class);
        warehouseMapper = mock(WarehouseMapper.class);
        listener = new OrderPaidListener(pickOrderService, warehouseMapper);
    }

    @Test
    @DisplayName("新事件信封：从 payload 提取 orderId；仓库取第一个可用")
    void envelopeShape_parsed() {
        Map<String, Object> message = new HashMap<>();
        message.put("eventId", "evt-2");
        message.put("eventType", "ORDER_PAID");
        message.put("payload", Map.of("orderId", 9001L, "userId", 42L));

        Warehouse warehouse = new Warehouse();
        warehouse.setId(7L);
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of(warehouse));

        assertThat(OrderPaidListener.extractOrderId(message)).isEqualTo(9001L);
        assertThat(listener.extractWarehouseIdOrDefault(message)).isEqualTo(7L);
    }

    @Test
    @DisplayName("事件未指定仓库且无可用仓库 → 显式失败（事件重试，不再默认仓库 1）")
    void noWarehouseAvailable_failsExplicitly() {
        Map<String, Object> message = new HashMap<>();
        message.put("payload", Map.of("orderId", 9001L));
        when(warehouseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThatThrownBy(() -> listener.extractWarehouseIdOrDefault(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无可用仓库");
    }

    @Test
    @DisplayName("旧格式：orderId/warehouseId 在顶层，仍可解析")
    void legacyShape_parsed() {
        Map<String, Object> message = new HashMap<>();
        message.put("orderId", 9002L);
        message.put("warehouseId", 3L);

        assertThat(OrderPaidListener.extractOrderId(message)).isEqualTo(9002L);
    }

    @Test
    @DisplayName("形状未命中时显式失败（触发 MQ 重试而非 NPE 吞消息）")
    void unknownShape_failsExplicitly() {
        assertThatThrownBy(() -> OrderPaidListener.extractOrderId(new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺少 orderId");
    }
}
