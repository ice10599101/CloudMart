package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderItemDTO;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.feign.CartFeignClient;
import com.cloudmart.order.feign.CouponFeignClient;
import com.cloudmart.order.feign.InventoryFeignClient;
import com.cloudmart.common.async.compensation.CompensationTaskService;
import com.cloudmart.common.async.outbox.OutboxService;
import tools.jackson.databind.ObjectMapper;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.order.mq.OrderEventProducer;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class OrderServiceImplTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderConverter orderConverter;
    private InventoryFeignClient inventoryFeignClient;
    private CartFeignClient cartFeignClient;
    private CouponFeignClient couponFeignClient;
    private StringRedisTemplate redisTemplate;
    private OrderEventProducer orderEventProducer;
    private OutboxService outboxService;
    private com.cloudmart.order.feign.WmsShippingFeignClient wmsShippingFeignClient;
    private CompensationTaskService compensationTaskService;
    private com.cloudmart.order.feign.RefundFeignClient refundFeignClient;
    private OrderServiceImpl orderService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        for (Class<?> clazz : new Class<?>[]{Order.class, OrderItem.class}) {
            if (TableInfoHelper.getTableInfo(clazz) == null) {
                MybatisConfiguration configuration = new MybatisConfiguration();
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                assistant.setCurrentNamespace("com.cloudmart.order.repository." + clazz.getSimpleName() + "Mapper");
                TableInfoHelper.initTableInfo(assistant, clazz);
            }
        }
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        orderConverter = mock(OrderConverter.class);
        inventoryFeignClient = mock(InventoryFeignClient.class);
        cartFeignClient = mock(CartFeignClient.class);
        couponFeignClient = mock(CouponFeignClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        orderEventProducer = mock(OrderEventProducer.class);

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        outboxService = mock(OutboxService.class);
        compensationTaskService = mock(CompensationTaskService.class);
        wmsShippingFeignClient = mock(com.cloudmart.order.feign.WmsShippingFeignClient.class);
        refundFeignClient = mock(com.cloudmart.order.feign.RefundFeignClient.class);
        orderService = new OrderServiceImpl(orderMapper, orderItemMapper, orderConverter,
                inventoryFeignClient, cartFeignClient, couponFeignClient, refundFeignClient,
                org.mockito.Mockito.mock(com.cloudmart.order.feign.ProductFeignClient.class),
                org.mockito.Mockito.mock(com.cloudmart.order.feign.RiskFeignClient.class),
                wmsShippingFeignClient,
                redisTemplate, orderEventProducer, outboxService, compensationTaskService,
                new ObjectMapper(),
                org.mockito.Mockito.mock(com.cloudmart.order.repository.OrderQuoteMapper.class),
                org.mockito.Mockito.mock(com.cloudmart.order.repository.OrderQuoteItemMapper.class),
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class));
    }

    private Order buildOrder(Long id, Long userId, String status) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo("ORD20260101000001");
        order.setStatus(status);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setPayAmount(new BigDecimal("100.00"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setReceiverName("张三");
        order.setReceiverPhone("13800138000");
        order.setReceiverAddress("北京市");
        order.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return order;
    }

    private OrderItem buildOrderItem(Long id, Long orderId) {
        OrderItem item = new OrderItem();
        item.setId(id);
        item.setOrderId(orderId);
        item.setProductId(1L);
        item.setSkuId(10L);
        item.setProductName("Test Product");
        item.setPrice(new BigDecimal("100.00"));
        item.setQuantity(1);
        return item;
    }

    @Nested
    @DisplayName("getOrderById")
    class GetOrderByIdTests {

        @Test
        @DisplayName("own order -> returns OrderDTO")
        void getOrderById_OwnOrder_ShouldReturnDTO() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            OrderDTO expected = new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "PENDING_PAYMENT", "张三", "13800138000", "北京市", null, null, null, null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(orderConverter.toDTO(eq(order), any())).thenReturn(expected);

            OrderDTO result = orderService.getOrderById(100L, 1L);

            assertThat(result).isEqualTo(expected);
            assertThat(result.status()).isEqualTo("PENDING_PAYMENT");
        }

        @Test
        @DisplayName("order not found -> throws ORDER_NOT_FOUND")
        void getOrderById_NotFound_ShouldThrowBusinessException() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.getOrderById(100L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("other user's order -> throws ORDER_ACCESS_DENIED")
        void getOrderById_OtherUser_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 200L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.getOrderById(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrderTests {

        @Test
        @DisplayName("pending payment order -> cancels successfully")
        void cancelOrder_PendingPayment_ShouldCancel() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusIfMatch(1L, "PENDING_PAYMENT", "CANCELLED")).thenReturn(1);

            Order cancelledOrder = buildOrder(1L, 100L, "CANCELLED");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(cancelledOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            OrderDTO expected = new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "CANCELLED", "张三", "13800138000", "北京市", null, null, null, null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(cancelledOrder, List.of(itemDTO))).thenReturn(expected);

            OrderDTO result = orderService.cancelOrder(100L, 1L);

            assertThat(result.status()).isEqualTo("CANCELLED");
            verify(outboxService).record(any(EventEnvelope.class));
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("order not found -> throws ORDER_NOT_FOUND")
        void cancelOrder_NotFound_ShouldThrowBusinessException() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.cancelOrder(100L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }

        @Test
        @DisplayName("other user's order -> throws ORDER_ACCESS_DENIED")
        void cancelOrder_OtherUser_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 200L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.cancelOrder(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("already paid order -> throws ORDER_STATUS_ERROR")
        void cancelOrder_AlreadyPaid_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.cancelOrder(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }
    }

    @Nested
    @DisplayName("shipOrder")
    class ShipOrderTests {

        @Test
        @DisplayName("paid order -> ships successfully")
        void shipOrder_PaidOrder_ShouldShip() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusAndShippedAtIfMatch(1L, "PAID", "SHIPPED")).thenReturn(1);

            // WMS-01 闭环：建包裹 + 出库
            when(wmsShippingFeignClient.createShipping(any())).thenReturn(
                    ApiResponse.ok(java.util.Map.of("id", 55L, "orderId", 1L)));
            when(wmsShippingFeignClient.updateStatus(55L, "SHIPPED")).thenReturn(
                    ApiResponse.ok(java.util.Map.of("id", 55L, "status", "SHIPPED")));

            Order shippedOrder = buildOrder(1L, 100L, "SHIPPED");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(shippedOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            OrderDTO expected = new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "SHIPPED", "张三", "13800138000", "北京市", null, null, null, null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(shippedOrder, List.of(itemDTO))).thenReturn(expected);

            OrderDTO result = orderService.shipOrder(1L, "顺丰", "SF1234567890", 10L);

            assertThat(result.status()).isEqualTo("SHIPPED");
            verify(outboxService).record(any(EventEnvelope.class));
            verify(wmsShippingFeignClient).createShipping(any());
            verify(wmsShippingFeignClient).updateStatus(55L, "SHIPPED");
        }

        @Test
        @DisplayName("not paid order -> throws ORDER_STATUS_ERROR")
        void shipOrder_NotPaid_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.shipOrder(1L, "顺丰", "SF123", 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }

        @Test
        @DisplayName("order not found -> throws ORDER_NOT_FOUND")
        void shipOrder_NotFound_ShouldThrowBusinessException() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.shipOrder(999L, "顺丰", "SF123", 10L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("autoConfirmReceipts（WMS-01 余量）")
    class AutoConfirmReceiptsTests {

        @Test
        @DisplayName("发货超期的订单批量 CAS 确认收货并发布事件")
        void autoConfirm_confirmsExpiredShippedOrders() {
            Order shipped = buildOrder(1L, 100L, "SHIPPED");
            when(orderMapper.findAutoConfirmableOrderIds(7, 500)).thenReturn(List.of(1L));
            when(orderMapper.selectById(1L)).thenReturn(shipped);
            when(orderMapper.updateStatusAndCompletedAtIfMatch(1L, "SHIPPED", "COMPLETED")).thenReturn(1);

            int confirmed = orderService.autoConfirmReceipts(7);

            assertThat(confirmed).isEqualTo(1);
            org.mockito.ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> captor =
                    org.mockito.ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
            verify(outboxService, org.mockito.Mockito.atLeastOnce()).record(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(com.cloudmart.common.async.EventEnvelope::eventType)
                    .contains("ORDER_STATUS_CHANGE");
        }

        @Test
        @DisplayName("非法天数（≤0）拒绝")
        void autoConfirm_invalidDays_rejected() {
            assertThatThrownBy(() -> orderService.autoConfirmReceipts(0))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("confirmReceipt")
    class ConfirmReceiptTests {

        @Test
        @DisplayName("shipped order -> confirms receipt")
        void confirmReceipt_ShippedOrder_ShouldConfirm() {
            Order order = buildOrder(1L, 100L, "SHIPPED");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusAndCompletedAtIfMatch(1L, "SHIPPED", "COMPLETED")).thenReturn(1);

            Order completedOrder = buildOrder(1L, 100L, "COMPLETED");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(completedOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            OrderDTO expected = new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "COMPLETED", "张三", "13800138000", "北京市", null, null, null, null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(completedOrder, List.of(itemDTO))).thenReturn(expected);

            OrderDTO result = orderService.confirmReceipt(100L, 1L);

            assertThat(result.status()).isEqualTo("COMPLETED");
            verify(outboxService).record(any(EventEnvelope.class));
        }

        @Test
        @DisplayName("not shipped order -> throws ORDER_STATUS_ERROR")
        void confirmReceipt_NotShipped_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.confirmReceipt(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }

        @Test
        @DisplayName("other user's order -> throws ORDER_ACCESS_DENIED")
        void confirmReceipt_OtherUser_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 200L, "SHIPPED");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.confirmReceipt(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("requestRefund")
    class RequestRefundTests {

        @Test
        @DisplayName("paid order -> requests refund successfully")
        void requestRefund_PaidOrder_ShouldRequestRefund() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusToRefunding(1L, "PAID", "REFUNDING", "defective")).thenReturn(1);

            Order refundingOrder = buildOrder(1L, 100L, "REFUNDING");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(refundingOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            OrderDTO expected = new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "REFUNDING", "张三", "13800138000", "北京市", null, null, "defective", null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(refundingOrder, List.of(itemDTO))).thenReturn(expected);

            OrderDTO result = orderService.requestRefund(100L, 1L, "defective");

            assertThat(result.status()).isEqualTo("REFUNDING");
            verify(outboxService).record(any(EventEnvelope.class));
        }

        @Test
        @DisplayName("shipped order -> can also request refund")
        void requestRefund_ShippedOrder_ShouldRequestRefund() {
            Order order = buildOrder(1L, 100L, "SHIPPED");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusToRefunding(1L, "SHIPPED", "REFUNDING", "wrong item")).thenReturn(1);

            Order refundingOrder = buildOrder(1L, 100L, "REFUNDING");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(refundingOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(refundingOrder, List.of(itemDTO))).thenReturn(
                    new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "REFUNDING", "张三", "13800138000", "北京市", null, null, "wrong item", null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null));

            OrderDTO result = orderService.requestRefund(100L, 1L, "wrong item");

            assertThat(result.status()).isEqualTo("REFUNDING");
        }

        @Test
        @DisplayName("pending payment order -> throws ORDER_STATUS_ERROR")
        void requestRefund_PendingPayment_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.requestRefund(100L, 1L, "reason"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }

        @Test
        @DisplayName("other user's order -> throws ORDER_ACCESS_DENIED")
        void requestRefund_OtherUser_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 200L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.requestRefund(100L, 1L, "reason"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("applyPaymentSucceeded（T05 唯一推进入口）")
    class ApplyPaymentSucceededTests {

        @Test
        @DisplayName("待支付订单 + 金额一致 → PAID，发布 ORDER_STATUS_CHANGE + ORDER_PAID（WMS 履约）")
        void applyPaymentSucceeded_pending_advancesToPaid() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusIfMatch(1L, "PENDING_PAYMENT", "PAID")).thenReturn(1);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            orderService.applyPaymentSucceeded(1L, "100.00", "CNY");

            verify(orderMapper).updateStatusIfMatch(1L, "PENDING_PAYMENT", "PAID");
            org.mockito.ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> envelopeCaptor =
                    org.mockito.ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
            verify(outboxService, org.mockito.Mockito.times(2)).record(envelopeCaptor.capture());
            assertThat(envelopeCaptor.getAllValues())
                    .extracting(com.cloudmart.common.async.EventEnvelope::eventType)
                    .containsExactly("ORDER_STATUS_CHANGE", "ORDER_PAID");
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("T05：金额与订单应付不一致 → PAYMENT_AMOUNT_MISMATCH，不推进")
        void applyPaymentSucceeded_amountMismatch_rejected() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.applyPaymentSucceeded(1L, "88.00", "CNY"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("PAYMENT_AMOUNT_MISMATCH"));
            verify(orderMapper, never()).updateStatusIfMatch(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("已推进（PAID）→ 事件重放幂等跳过")
        void applyPaymentSucceeded_alreadyPaid_idempotentSkip() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            orderService.applyPaymentSucceeded(1L, "100.00", "CNY");

            verify(orderMapper, never()).updateStatusIfMatch(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("QA05：已取消订单收到支付成功 → LATE_PAYMENT_DETECTED 事件，不推进也不静默丢弃")
        void applyPaymentSucceeded_cancelled_latePaymentDetected() {
            Order order = buildOrder(1L, 100L, "CANCELLED");
            when(orderMapper.selectById(1L)).thenReturn(order);

            orderService.applyPaymentSucceeded(1L, "100.00", "CNY");

            verify(orderMapper, never()).updateStatusIfMatch(anyLong(), anyString(), anyString());
            org.mockito.ArgumentCaptor<com.cloudmart.common.async.EventEnvelope> envelopeCaptor =
                    org.mockito.ArgumentCaptor.forClass(com.cloudmart.common.async.EventEnvelope.class);
            verify(outboxService).record(envelopeCaptor.capture());
            assertThat(envelopeCaptor.getValue().eventType()).isEqualTo("LATE_PAYMENT_DETECTED");
        }

        @Test
        @DisplayName("订单不存在 → ORDER_NOT_FOUND（明确失败，消费者重试/死信）")
        void applyPaymentSucceeded_notFound_throws() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.applyPaymentSucceeded(999L, "100.00", "CNY"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("approveRefund")
    class ApproveRefundTests {

        @Test
        @DisplayName("T02：审批提交退款单 → MOCK 渠道同步 SUCCEEDED → 推进 REFUNDED + 释放库存 + 退券")
        void approveRefund_RefundingOrder_ChannelSucceeded_advances() {
            Order order = buildOrder(1L, 100L, "REFUNDING");
            order.setCouponId(50L);
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusToRefunded(1L, "REFUNDING", "REFUNDED")).thenReturn(1);

            java.util.Map<String, Object> refundView = java.util.Map.of(
                    "refundNo", "RF1", "status", "SUCCEEDED", "providerRefundNo", "MOCKRFND1");
            when(refundFeignClient.createRefund(any())).thenReturn(ApiResponse.ok(refundView));

            Order refundedOrder = buildOrder(1L, 100L, "REFUNDED");
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(refundedOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of());
            when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(
                    new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, 50L, "REFUNDED", "张三", "13800138000", "北京市", null, null, null, null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), null));

            OrderDTO result = orderService.approveRefund(1L);

            assertThat(result.status()).isEqualTo("REFUNDED");
            verify(refundFeignClient).createRefund(any());
            verify(orderMapper).updateStatusToRefunded(1L, "REFUNDING", "REFUNDED");
            verify(outboxService).record(any(EventEnvelope.class));
        }

        @Test
        @DisplayName("T02/QA06：退款提交失败 → REFUND_SUBMIT_FAILED，不推进订单")
        void approveRefund_submitFailed_throwsAndKeepsRefunding() {
            Order order = buildOrder(1L, 100L, "REFUNDING");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(refundFeignClient.createRefund(any())).thenReturn(ApiResponse.ok(null));

            assertThatThrownBy(() -> orderService.approveRefund(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                            .isEqualTo("REFUND_SUBMIT_FAILED"));
            verify(orderMapper, never()).updateStatusToRefunded(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("not refunding order -> throws ORDER_STATUS_ERROR")
        void approveRefund_NotRefunding_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.approveRefund(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }
    }
}
