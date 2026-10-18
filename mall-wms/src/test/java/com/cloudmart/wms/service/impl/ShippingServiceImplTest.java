package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.dto.ShippingOrderDTO;
import com.cloudmart.wms.dto.ShippingTrackingDTO;
import com.cloudmart.wms.entity.ShippingOrder;
import com.cloudmart.wms.entity.ShippingTracking;
import com.cloudmart.wms.repository.ShippingOrderMapper;
import com.cloudmart.wms.repository.ShippingTrackingMapper;
import com.cloudmart.wms.vo.ShippingOrderVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceImplTest {

    @Mock
    private ShippingOrderMapper shippingOrderMapper;

    @Mock
    private ShippingTrackingMapper shippingTrackingMapper;

    @Mock
    private WmsConverter wmsConverter;

    @Mock
    private com.cloudmart.wms.repository.WarehouseMapper warehouseMapper;

    private ShippingServiceImpl shippingService;
    private com.cloudmart.common.async.outbox.OutboxService outboxService;
    private com.cloudmart.wms.feign.OrderInfoFeignClient orderInfoFeignClient;

    @BeforeEach
    void setUp() {
        outboxService = org.mockito.Mockito.mock(com.cloudmart.common.async.outbox.OutboxService.class);
        orderInfoFeignClient = org.mockito.Mockito.mock(com.cloudmart.wms.feign.OrderInfoFeignClient.class);
        shippingService = new ShippingServiceImpl(shippingOrderMapper, shippingTrackingMapper,
                wmsConverter, orderInfoFeignClient,
                outboxService, warehouseMapper, new tools.jackson.databind.ObjectMapper());
    }

    private static final Long SHIPPING_ORDER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long WAREHOUSE_ID = 10L;

    @Nested
    @DisplayName("createShippingOrder")
    class CreateShippingTests {

        @Test
        @DisplayName("should create shipping order and return VO")
        void createShippingOrder_success_returnsVO() {
            // WMS-01：warehouseId 可空 → 服务端分配第一个可用仓库
            var warehouse = new com.cloudmart.wms.entity.Warehouse();
            warehouse.setId(WAREHOUSE_ID);
            warehouse.setName("主仓");
            when(warehouseMapper.selectList(any())).thenReturn(java.util.List.of(warehouse));

            CreateShippingRequest request = new CreateShippingRequest(
                    ORDER_ID, null, "顺丰", "SF1234567890", "张三", "13800138000", "北京市朝阳区");

            ShippingOrderVO expectedVO = new ShippingOrderVO(
                    SHIPPING_ORDER_ID, ORDER_ID, 1L, "SF123456", null, "顺丰", "PENDING", null, null);

            when(shippingOrderMapper.insert(any(ShippingOrder.class))).thenReturn(1);
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class))).thenReturn(expectedVO);

            ShippingOrderVO result = shippingService.createShipping(request);

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
            assertThat(result.carrier()).isEqualTo("顺丰");
            assertThat(result.status()).isEqualTo("PENDING");
            verify(shippingOrderMapper).insert(any(ShippingOrder.class));
        }
    }

    @Nested
    @DisplayName("getByOrderId")
    class GetByOrderIdTests {

        @Test
        @DisplayName("should return shipping order VO when found")
        void getByOrderId_existing_returnsVO() {
            ShippingOrder order = new ShippingOrder();
            order.setId(SHIPPING_ORDER_ID);
            order.setOrderId(ORDER_ID);
            order.setShippingNo("SF123456");
            order.setCarrier("顺丰");
            order.setStatus("SHIPPED");

            ShippingOrderVO expectedVO = new ShippingOrderVO(
                    SHIPPING_ORDER_ID, ORDER_ID, 1L, "SF123456", "SF1234567890", "顺丰", "SHIPPED", null, null);

            when(shippingOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
            when(shippingTrackingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class))).thenReturn(expectedVO);

            ShippingOrderVO result = shippingService.getByOrderId(ORDER_ID, null);

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("should throw when shipping order not found")
        void getByOrderId_nonExistent_throwsException() {
            when(shippingOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> shippingService.getByOrderId(ORDER_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("SHIPPING_ORDER_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("updateShippingStatus")
    class UpdateStatusTests {

        @Test
        @DisplayName("should update status and return VO")
        void updateShippingStatus_existing_updatesAndReturnsVO() {
            // WMS-01：状态机白名单——PENDING → PICKING 合法迁移；CAS 更新
            ShippingOrder order = new ShippingOrder();
            order.setId(SHIPPING_ORDER_ID);
            order.setOrderId(ORDER_ID);
            order.setStatus("PENDING");
            order.setTrackingNo("SF1234567890");

            ShippingOrderVO expectedVO = new ShippingOrderVO(
                    SHIPPING_ORDER_ID, ORDER_ID, 1L, "SF123456", null, "顺丰", "PICKING", null, null);

            // WMS-01：CAS 更新后 service 会回查刷新（两次 selectById）
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(order);
            when(shippingOrderMapper.updateStatusPlainIfMatch(SHIPPING_ORDER_ID, "PENDING", "PICKING"))
                    .thenReturn(1);

            when(shippingTrackingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class))).thenReturn(expectedVO);

            ShippingOrderVO result = shippingService.updateStatus(SHIPPING_ORDER_ID, "PICKING");

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo("PICKING");
        }

        @Test
        @DisplayName("should throw when shipping order not found")
        void updateShippingStatus_nonExistent_throwsException() {
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> shippingService.updateStatus(SHIPPING_ORDER_ID, "PICKING"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("SHIPPING_ORDER_NOT_FOUND");
        }

        @Test
        @DisplayName("T05：禁止 PENDING 直接 DELIVERED（跳过出库事实）")
        void updateShippingStatus_pendingToDelivered_rejected() {
            ShippingOrder order = new ShippingOrder();
            order.setId(SHIPPING_ORDER_ID);
            order.setOrderId(ORDER_ID);
            order.setStatus("PENDING");
            order.setTrackingNo("SF1234567890");
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(order);

            assertThatThrownBy(() -> shippingService.updateStatus(SHIPPING_ORDER_ID, "DELIVERED"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("SHIPPING_STATUS_TRANSITION_INVALID");
            verify(outboxService, org.mockito.Mockito.never()).record(any());
        }

        @Test
        @DisplayName("T05：出库写稳定事件 ID 与类型化载荷（含 shipmentId）")
        void updateShippingStatus_shipped_recordsTypedEvent() {
            ShippingOrder pending = new ShippingOrder();
            pending.setId(SHIPPING_ORDER_ID);
            pending.setOrderId(ORDER_ID);
            pending.setCarrier("顺丰\"特快\"");   // 含引号：手工拼 JSON 会坏消息
            pending.setTrackingNo("SF1234567890");
            pending.setStatus("PENDING");

            ShippingOrder shipped = new ShippingOrder();
            shipped.setId(SHIPPING_ORDER_ID);
            shipped.setOrderId(ORDER_ID);
            shipped.setCarrier("顺丰\"特快\"");
            shipped.setTrackingNo("SF1234567890");
            shipped.setStatus("SHIPPED");

            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID))
                    .thenReturn(pending, shipped, shipped);
            when(shippingOrderMapper.updateStatusIfMatch(SHIPPING_ORDER_ID, "PENDING", "SHIPPED"))
                    .thenReturn(1);
            when(orderInfoFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                    com.cloudmart.common.api.ApiResponse.ok(
                            new com.cloudmart.wms.dto.OrderInternalInfoDTO(ORDER_ID, 1L, "PAID")));
            when(shippingTrackingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class)))
                    .thenReturn(new ShippingOrderVO(SHIPPING_ORDER_ID, ORDER_ID, 1L, "SF1",
                            "SF1234567890", "顺丰\"特快\"", "SHIPPED", null, null));

            shippingService.updateStatus(SHIPPING_ORDER_ID, "SHIPPED");

            org.mockito.ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                    org.mockito.ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
            verify(outboxService).record(captor.capture());
            com.cloudmart.common.async.EventEnvelope envelope = captor.getValue();
            assertThat(envelope.eventId()).isEqualTo("ORDER_SHIPPED:" + ORDER_ID);
            assertThat(envelope.schemaVersion()).isEqualTo(2);
            assertThat(envelope.payload()).contains("\"shipmentId\":" + SHIPPING_ORDER_ID);
            assertThat(envelope.payload()).contains("顺丰\\\"特快\\\"");
        }

        @Test
        @DisplayName("T05：订单已取消时禁止出库，且不写 Outbox")
        void updateShippingStatus_orderCancelled_rejected() {
            ShippingOrder pending = new ShippingOrder();
            pending.setId(SHIPPING_ORDER_ID);
            pending.setOrderId(ORDER_ID);
            pending.setTrackingNo("SF1234567890");
            pending.setStatus("PENDING");
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(pending);
            when(orderInfoFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                    com.cloudmart.common.api.ApiResponse.ok(
                            new com.cloudmart.wms.dto.OrderInternalInfoDTO(ORDER_ID, 1L, "CANCELLED")));

            assertThatThrownBy(() -> shippingService.updateStatus(SHIPPING_ORDER_ID, "SHIPPED"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("SHIPPING_ORDER_NOT_SHIPPABLE");
            verify(outboxService, org.mockito.Mockito.never()).record(any());
            verify(shippingOrderMapper, org.mockito.Mockito.never())
                    .updateStatusIfMatch(any(), any(), any());
        }

        @Test
        @DisplayName("T05：Outbox 记录失败必须上抛（触发状态回滚），不得返回成功")
        void updateShippingStatus_outboxFails_propagates() {
            ShippingOrder pending = new ShippingOrder();
            pending.setId(SHIPPING_ORDER_ID);
            pending.setOrderId(ORDER_ID);
            pending.setTrackingNo("SF1234567890");
            pending.setStatus("PENDING");

            ShippingOrder shipped = new ShippingOrder();
            shipped.setId(SHIPPING_ORDER_ID);
            shipped.setOrderId(ORDER_ID);
            shipped.setTrackingNo("SF1234567890");
            shipped.setStatus("SHIPPED");

            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID))
                    .thenReturn(pending, shipped, shipped);
            when(shippingOrderMapper.updateStatusIfMatch(SHIPPING_ORDER_ID, "PENDING", "SHIPPED"))
                    .thenReturn(1);
            when(orderInfoFeignClient.getOrderInfo(ORDER_ID)).thenReturn(
                    com.cloudmart.common.api.ApiResponse.ok(
                            new com.cloudmart.wms.dto.OrderInternalInfoDTO(ORDER_ID, 1L, "PAID")));
            org.mockito.Mockito.doThrow(new IllegalStateException("outbox down"))
                    .when(outboxService).record(any());

            assertThatThrownBy(() -> shippingService.updateStatus(SHIPPING_ORDER_ID, "SHIPPED"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("T05：重复相同终态按业务键返回原结果（幂等，不报错）")
        void updateShippingStatus_sameStatus_idempotent() {
            ShippingOrder shipped = new ShippingOrder();
            shipped.setId(SHIPPING_ORDER_ID);
            shipped.setOrderId(ORDER_ID);
            shipped.setStatus("DELIVERED");
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(shipped);
            when(shippingTrackingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class)))
                    .thenReturn(new ShippingOrderVO(SHIPPING_ORDER_ID, ORDER_ID, 1L, "SF1",
                            "SF1234567890", "顺丰", "DELIVERED", null, null));

            ShippingOrderVO result = shippingService.updateStatus(SHIPPING_ORDER_ID, "DELIVERED");

            assertThat(result.status()).isEqualTo("DELIVERED");
            verify(outboxService, org.mockito.Mockito.never()).record(any());
        }
    }

    @Nested
    @DisplayName("addTracking")
    class AddTrackingTests {

        @Test
        @DisplayName("should add tracking and return DTO")
        void addTracking_existingOrder_returnsTrackingDTO() {
            ShippingOrder order = new ShippingOrder();
            order.setId(SHIPPING_ORDER_ID);
            order.setStatus("IN_TRANSIT");

            LocalDateTime happenedAt = LocalDateTime.of(2026, 1, 1, 12, 0);

            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(order);
            when(shippingTrackingMapper.insert(any(ShippingTracking.class))).thenReturn(1);

            ShippingTrackingDTO result = shippingService.addTracking(
                    SHIPPING_ORDER_ID, "北京分拨中心", "已到达", happenedAt);

            assertThat(result).isNotNull();
            assertThat(result.shippingOrderId()).isEqualTo(SHIPPING_ORDER_ID);
            assertThat(result.location()).isEqualTo("北京分拨中心");
            assertThat(result.description()).isEqualTo("已到达");
            verify(shippingTrackingMapper).insert(any(ShippingTracking.class));
        }

        @Test
        @DisplayName("should throw when shipping order not found")
        void addTracking_nonExistentOrder_throwsException() {
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> shippingService.addTracking(
                    SHIPPING_ORDER_ID, "北京", "已到达", LocalDateTime.now()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("SHIPPING_ORDER_NOT_FOUND");
        }
    }
}
