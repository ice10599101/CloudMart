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
    private com.cloudmart.order.service.AfterSaleCaseService afterSaleCaseService;
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
                new OrderCouponPolicy(couponFeignClient),
                org.mockito.Mockito.mock(com.cloudmart.order.feign.ProductFeignClient.class),
                org.mockito.Mockito.mock(com.cloudmart.order.feign.RiskFeignClient.class),
                wmsShippingFeignClient,
                org.mockito.Mockito.mock(com.cloudmart.order.feign.SeckillFeignClient.class),
                org.mockito.Mockito.mock(com.cloudmart.order.feign.MarketingFeignClient.class),
                afterSaleCaseService = mock(com.cloudmart.order.service.AfterSaleCaseService.class),
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
    @DisplayName("requestRefund（T04：适配为整单售后案件，不改订单状态）")
    class RequestRefundTests {

        @Test
        @DisplayName("PAID 订单 → 创建整单售后案件（委托 case 服务），订单状态不变")
        void requestRefund_PaidOrder_CreatesWholeOrderCase() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of());
            when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(
                    new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "PAID", "张三", "13800138000", "北京市", null, null, null, null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), null));

            OrderDTO result = orderService.requestRefund(100L, 1L, "defective");

            assertThat(result.status()).isEqualTo("PAID");
            var caseCaptor = org.mockito.ArgumentCaptor.forClass(
                    com.cloudmart.order.dto.CreateAfterSaleRequest.class);
            verify(afterSaleCaseService).apply(org.mockito.ArgumentMatchers.eq(100L), caseCaptor.capture());
            assertThat(caseCaptor.getValue().orderId()).isEqualTo(1L);
            assertThat(caseCaptor.getValue().itemId()).isNull();
            assertThat(caseCaptor.getValue().type()).isEqualTo(
                    com.cloudmart.order.entity.AfterSaleCase.TYPE_REFUND_ONLY);
            // 订单状态不再推入 REFUNDING（资金只走 case 通路）
            verify(orderMapper, never()).updateStatusToRefunding(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("order missing -> throws ORDER_NOT_FOUND")
        void requestRefund_OrderMissing_ShouldThrowBusinessException() {
            when(orderMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.requestRefund(100L, 1L, "reason"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
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
    @DisplayName("approveRefund（T04：委托整单案件审批，异步提交退款）")
    class ApproveRefundTests {

        @Test
        @DisplayName("存在 PENDING 整单案件 → 按实付-占用金额委托 case 审批")
        void approveRefund_pendingWholeOrderCase_delegatesApproval() {
            Order order = buildOrder(1L, 100L, "PAID");
            order.setPayAmount(new BigDecimal("100.00"));
            when(orderMapper.selectById(1L)).thenReturn(order);
            var pendingCase = new com.cloudmart.order.entity.AfterSaleCase();
            pendingCase.setId(11L);
            pendingCase.setOrderId(1L);
            pendingCase.setStatus(com.cloudmart.order.entity.AfterSaleCase.STATUS_PENDING);
            when(afterSaleCaseService.findPendingWholeOrderCase(1L)).thenReturn(pendingCase);
            when(afterSaleCaseService.occupiedRefundAmount(1L, 11L)).thenReturn(BigDecimal.ZERO);
            when(afterSaleCaseService.approve(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq(11L),
                    org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")))).thenReturn(null);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of());
            when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(
                    new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "PAID", "张三", "13800138000", "北京市", null, null, null, null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), null));

            orderService.approveRefund(1L);

            verify(afterSaleCaseService).approve(org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq(11L), org.mockito.ArgumentMatchers.eq(new BigDecimal("100.00")));
        }

        @Test
        @DisplayName("无待审批整单案件 → ORDER_STATUS_ERROR（不再直接发款）")
        void approveRefund_noPendingCase_throws() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(afterSaleCaseService.findPendingWholeOrderCase(1L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.approveRefund(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
            verify(refundFeignClient, never()).createRefund(any());
        }

        @Test
        @DisplayName("额度已被其他案件占用 → 幂等返回，不再发款")
        void approveRefund_quotaOccupied_idempotent() {
            Order order = buildOrder(1L, 100L, "PAID");
            order.setPayAmount(new BigDecimal("100.00"));
            when(orderMapper.selectById(1L)).thenReturn(order);
            var pendingCase = new com.cloudmart.order.entity.AfterSaleCase();
            pendingCase.setId(11L);
            when(afterSaleCaseService.findPendingWholeOrderCase(1L)).thenReturn(pendingCase);
            when(afterSaleCaseService.occupiedRefundAmount(1L, 11L)).thenReturn(new BigDecimal("100.00"));
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of());
            when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(
                    new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "PAID", "张三", "13800138000", "北京市", null, null, null, null, List.of(), LocalDateTime.of(2026, 1, 1, 0, 0), null));

            orderService.approveRefund(1L);

            verify(afterSaleCaseService, never()).approve(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        }
    }
    @Nested
    @DisplayName("onAfterSaleRefundCompleted（T04：案件退款完成结算）")
    class AfterSaleRefundSettlementTests {

        private com.cloudmart.order.entity.AfterSaleCase refundedCase(BigDecimal amount) {
            var c = new com.cloudmart.order.entity.AfterSaleCase();
            c.setId(11L);
            c.setOrderId(1L);
            c.setRefundNo("RFC11");
            c.setRefundAmount(amount);
            c.setStatus(com.cloudmart.order.entity.AfterSaleCase.STATUS_APPROVED);
            return c;
        }

        @Test
        @DisplayName("部分退款：案件回填 + 已退累计 PARTIAL，履约状态/库存/券均不动")
        void partialRefund_accumulatesOnly() {
            Order order = buildOrder(1L, 100L, "PAID");
            order.setPayAmount(new BigDecimal("100.00"));
            order.setCouponId(50L);
            when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
            when(afterSaleCaseService.markRefundedReturning("RFC11"))
                    .thenReturn(refundedCase(new BigDecimal("40.00")));

            orderService.onAfterSaleRefundCompleted("RFC11");

            verify(orderMapper).updateRefundSummary(1L, new BigDecimal("40.00"), "PARTIAL");
            verify(orderMapper, never()).updateStatusToRefundedForFullRefund(anyLong());
            verify(inventoryFeignClient, never()).releaseStock(any());
            verify(couponFeignClient, never()).returnCoupon(any());
        }

        @Test
        @DisplayName("全额退款（未发货）：订单推进 REFUNDED + 释放预占库存 + 整单返券")
        void fullRefund_unshipped_releasesStockAndCoupon() {
            Order order = buildOrder(1L, 100L, "PAID");
            order.setPayAmount(new BigDecimal("100.00"));
            order.setCouponId(50L);
            when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
            when(afterSaleCaseService.markRefundedReturning("RFC11"))
                    .thenReturn(refundedCase(new BigDecimal("100.00")));
            when(orderMapper.updateStatusToRefundedForFullRefund(1L)).thenReturn(1);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(buildOrderItem(1L, 1L)));

            orderService.onAfterSaleRefundCompleted("RFC11");

            verify(orderMapper).updateRefundSummary(1L, new BigDecimal("100.00"), "FULL");
            verify(orderMapper).updateStatusToRefundedForFullRefund(1L);
            verify(outboxService).record(any(EventEnvelope.class));
            verify(inventoryFeignClient).releaseStock(any());
            verify(couponFeignClient).returnCoupon(any());
        }

        @Test
        @DisplayName("全额退款（已发货）：不释放库存（已确认销售走 WMS 退货入库），仍整单返券")
        void fullRefund_shipped_noStockRelease() {
            Order order = buildOrder(1L, 100L, "SHIPPED");
            order.setPayAmount(new BigDecimal("100.00"));
            order.setCouponId(50L);
            when(orderMapper.selectByIdForUpdate(1L)).thenReturn(order);
            when(afterSaleCaseService.markRefundedReturning("RFC11"))
                    .thenReturn(refundedCase(new BigDecimal("100.00")));
            when(orderMapper.updateStatusToRefundedForFullRefund(1L)).thenReturn(1);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            orderService.onAfterSaleRefundCompleted("RFC11");

            verify(orderMapper).updateStatusToRefundedForFullRefund(1L);
            verify(inventoryFeignClient, never()).releaseStock(any());
            verify(couponFeignClient).returnCoupon(any());
        }

        @Test
        @DisplayName("重复/乱序通知：案件未命中 → 幂等无操作")
        void duplicateNotification_idempotent() {
            when(afterSaleCaseService.markRefundedReturning("RFC11")).thenReturn(null);

            orderService.onAfterSaleRefundCompleted("RFC11");

            verify(orderMapper, never()).selectByIdForUpdate(anyLong());
            verify(orderMapper, never()).updateRefundSummary(anyLong(), any(), any());
        }
    }

}
