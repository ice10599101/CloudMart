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

    private ShippingServiceImpl shippingService;

    @BeforeEach
    void setUp() {
        shippingService = new ShippingServiceImpl(shippingOrderMapper, shippingTrackingMapper, wmsConverter);
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
            CreateShippingRequest request = new CreateShippingRequest(
                    ORDER_ID, WAREHOUSE_ID, "顺丰", "张三", "13800138000", "北京市朝阳区");

            ShippingOrderVO expectedVO = new ShippingOrderVO(
                    SHIPPING_ORDER_ID, ORDER_ID, "SF123456", "顺丰", "PENDING", null);

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
                    SHIPPING_ORDER_ID, ORDER_ID, "SF123456", "顺丰", "SHIPPED", null);

            when(shippingOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
            when(shippingTrackingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class))).thenReturn(expectedVO);

            ShippingOrderVO result = shippingService.getByOrderId(ORDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(ORDER_ID);
        }

        @Test
        @DisplayName("should throw when shipping order not found")
        void getByOrderId_nonExistent_throwsException() {
            when(shippingOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThatThrownBy(() -> shippingService.getByOrderId(ORDER_ID))
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
            ShippingOrder order = new ShippingOrder();
            order.setId(SHIPPING_ORDER_ID);
            order.setOrderId(ORDER_ID);
            order.setStatus("PENDING");

            ShippingOrderVO expectedVO = new ShippingOrderVO(
                    SHIPPING_ORDER_ID, ORDER_ID, "SF123456", "顺丰", "IN_TRANSIT", null);

            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(order);
            when(shippingOrderMapper.updateById(order)).thenReturn(1);
            when(shippingTrackingMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(wmsConverter.fromShippingOrderDTO(any(ShippingOrderDTO.class))).thenReturn(expectedVO);

            ShippingOrderVO result = shippingService.updateStatus(SHIPPING_ORDER_ID, "IN_TRANSIT");

            assertThat(result).isNotNull();
            assertThat(order.getStatus()).isEqualTo("IN_TRANSIT");
        }

        @Test
        @DisplayName("should throw when shipping order not found")
        void updateShippingStatus_nonExistent_throwsException() {
            when(shippingOrderMapper.selectById(SHIPPING_ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> shippingService.updateStatus(SHIPPING_ORDER_ID, "IN_TRANSIT"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo("SHIPPING_ORDER_NOT_FOUND");
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
