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
import com.cloudmart.order.feign.PaymentFeignClient;
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
    private PaymentFeignClient paymentFeignClient;
    private CouponFeignClient couponFeignClient;
    private StringRedisTemplate redisTemplate;
    private OrderEventProducer orderEventProducer;
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
        paymentFeignClient = mock(PaymentFeignClient.class);
        couponFeignClient = mock(CouponFeignClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        orderEventProducer = mock(OrderEventProducer.class);

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        orderService = new OrderServiceImpl(orderMapper, orderItemMapper, orderConverter,
                inventoryFeignClient, cartFeignClient, paymentFeignClient, couponFeignClient,
                redisTemplate, orderEventProducer);
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
            verify(orderEventProducer).sendOrderStatusChange(any());
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

            Order shippedOrder = buildOrder(1L, 100L, "SHIPPED");
            OrderItem item = buildOrderItem(1L, 1L);
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(shippedOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            OrderDTO expected = new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "SHIPPED", "张三", "13800138000", "北京市", null, null, null, null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(shippedOrder, List.of(itemDTO))).thenReturn(expected);

            OrderDTO result = orderService.shipOrder(1L);

            assertThat(result.status()).isEqualTo("SHIPPED");
            verify(orderEventProducer).sendOrderStatusChange(any());
        }

        @Test
        @DisplayName("not paid order -> throws ORDER_STATUS_ERROR")
        void shipOrder_NotPaid_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.shipOrder(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }

        @Test
        @DisplayName("order not found -> throws ORDER_NOT_FOUND")
        void shipOrder_NotFound_ShouldThrowBusinessException() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.shipOrder(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
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
            verify(orderEventProducer).sendOrderStatusChange(any());
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
            verify(orderEventProducer).sendOrderStatusChange(any());
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
    @DisplayName("payForOrder")
    class PayForOrderTests {

        @Test
        @DisplayName("pending payment order -> creates payment")
        void payForOrder_PendingPayment_ShouldCreatePayment() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            PaymentFeignClient.PaymentDTO paymentDTO = new PaymentFeignClient.PaymentDTO(1L, 1L, "PAY001", new BigDecimal("100.00"), null, "PENDING", null, null, "http://pay.url");
            when(paymentFeignClient.createPayment(any(PaymentFeignClient.CreatePaymentRequest.class)))
                    .thenReturn(ApiResponse.ok(paymentDTO));

            PaymentFeignClient.PaymentDTO result = orderService.payForOrder(100L, 1L);

            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("already paid order -> throws ORDER_STATUS_ERROR")
        void payForOrder_AlreadyPaid_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.payForOrder(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_STATUS_ERROR"));
        }

        @Test
        @DisplayName("other user's order -> throws ORDER_ACCESS_DENIED")
        void payForOrder_OtherUser_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 200L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);

            assertThatThrownBy(() -> orderService.payForOrder(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("payment creation fails -> throws PAYMENT_CREATE_FAILED")
        void payForOrder_PaymentFails_ShouldThrowBusinessException() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(paymentFeignClient.createPayment(any(PaymentFeignClient.CreatePaymentRequest.class)))
                    .thenReturn(ApiResponse.ok(null));

            assertThatThrownBy(() -> orderService.payForOrder(100L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("PAYMENT_CREATE_FAILED"));
        }
    }

    @Nested
    @DisplayName("notifyPaymentSuccess")
    class NotifyPaymentSuccessTests {

        @Test
        @DisplayName("pending payment order -> updates to PAID")
        void notifyPaymentSuccess_PendingPayment_ShouldUpdateToPaid() {
            Order order = buildOrder(1L, 100L, "PENDING_PAYMENT");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusIfMatch(1L, "PENDING_PAYMENT", "PAID")).thenReturn(1);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            orderService.notifyPaymentSuccess(1L);

            verify(orderMapper).updateStatusIfMatch(1L, "PENDING_PAYMENT", "PAID");
            verify(orderEventProducer).sendOrderStatusChange(any());
            verify(redisTemplate).delete(anyString());
        }

        @Test
        @DisplayName("already paid order -> does nothing")
        void notifyPaymentSuccess_AlreadyPaid_ShouldDoNothing() {
            Order order = buildOrder(1L, 100L, "PAID");
            when(orderMapper.selectById(1L)).thenReturn(order);

            orderService.notifyPaymentSuccess(1L);

            verify(orderMapper, never()).updateStatusIfMatch(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("order not found -> throws ORDER_NOT_FOUND")
        void notifyPaymentSuccess_NotFound_ShouldThrowBusinessException() {
            when(orderMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.notifyPaymentSuccess(999L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ORDER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("approveRefund")
    class ApproveRefundTests {

        @Test
        @DisplayName("refunding order -> approves refund")
        void approveRefund_RefundingOrder_ShouldApprove() {
            Order order = buildOrder(1L, 100L, "REFUNDING");
            when(orderMapper.selectById(1L)).thenReturn(order);
            when(orderMapper.updateStatusToRefunded(1L, "REFUNDING", "REFUNDED")).thenReturn(1);

            PaymentFeignClient.PaymentDTO paymentDTO = new PaymentFeignClient.PaymentDTO(1L, 1L, "PAY001", new BigDecimal("100.00"), null, "PAID", null, null, null);
            when(paymentFeignClient.getPaymentByOrderId(1L)).thenReturn(ApiResponse.ok(paymentDTO));
            when(paymentFeignClient.refund(1L)).thenReturn(ApiResponse.ok(paymentDTO));

            OrderItem item = buildOrderItem(1L, 1L);
            Order refundedOrder = buildOrder(1L, 100L, "REFUNDED");
            when(orderMapper.selectById(1L)).thenReturn(order).thenReturn(refundedOrder);
            when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(item));

            OrderItemDTO itemDTO = new OrderItemDTO(1L, 1L, 10L, "Test Product", null, null, new BigDecimal("100.00"), 1);
            when(orderConverter.toItemDTOList(List.of(item))).thenReturn(List.of(itemDTO));
            when(orderConverter.toDTO(refundedOrder, List.of(itemDTO))).thenReturn(
                    new OrderDTO(1L, "ORD20260101000001", new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO, null, "REFUNDED", "张三", "13800138000", "北京市", null, null, null, null, List.of(itemDTO), LocalDateTime.of(2026, 1, 1, 0, 0), null));

            OrderDTO result = orderService.approveRefund(1L);

            assertThat(result.status()).isEqualTo("REFUNDED");
            verify(paymentFeignClient).refund(1L);
            verify(orderEventProducer).sendOrderStatusChange(any());
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
