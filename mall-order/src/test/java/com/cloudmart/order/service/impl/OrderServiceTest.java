package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.InventoryDeductRequest;
import com.cloudmart.order.dto.InventoryReleaseRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderItemDTO;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.feign.CartFeignClient;
import com.cloudmart.order.feign.CouponFeignClient;
import com.cloudmart.order.feign.InventoryFeignClient;
import com.cloudmart.order.feign.PaymentFeignClient;
import com.cloudmart.order.mq.OrderEventProducer;
import com.cloudmart.order.mq.OrderStatusChangeMessage;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderConverter orderConverter;
    private InventoryFeignClient inventoryFeignClient;
    private CartFeignClient cartFeignClient;
    private PaymentFeignClient paymentFeignClient;
    private CouponFeignClient couponFeignClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OrderEventProducer orderEventProducer;
    private OrderServiceImpl orderService;

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
        valueOperations = mock(ValueOperations.class);
        orderEventProducer = mock(OrderEventProducer.class);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        orderService = new OrderServiceImpl(
                orderMapper, orderItemMapper, orderConverter,
                inventoryFeignClient, cartFeignClient, paymentFeignClient,
                couponFeignClient, redisTemplate, orderEventProducer
        );
    }

    @Test
    void cancelOrder_WhenOrderIsPendingPayment_ShouldCancelAndReleaseStock() {
        Long userId = 1L;
        Long orderId = 100L;

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus("PENDING_PAYMENT");
        order.setCouponId(null);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrderId(orderId);
        orderItem.setSkuId(200L);
        orderItem.setQuantity(2);

        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO expectedDto = new OrderDTO(orderId, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"), BigDecimal.ZERO, null, "CANCELLED", "张三", "13800138000", "地址", null, null, null, null, List.of(itemDto), null, null);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "CANCELLED")).thenReturn(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orderItem));
        when(inventoryFeignClient.releaseStock(any(InventoryReleaseRequest.class))).thenReturn(ApiResponse.ok(null));
        when(redisTemplate.delete(anyString())).thenReturn(true);
        when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of(itemDto));
        when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(expectedDto);

        OrderDTO result = orderService.cancelOrder(userId, orderId);

        assertThat(result).isEqualTo(expectedDto);
        verify(orderMapper).updateStatusIfMatch(orderId, "PENDING_PAYMENT", "CANCELLED");
        verify(orderEventProducer).sendOrderStatusChange(argThat(msg ->
                msg.orderId().equals(orderId)
                        && msg.userId().equals(userId)
                        && "PENDING_PAYMENT".equals(msg.oldStatus())
                        && "CANCELLED".equals(msg.newStatus())
        ));
        verify(inventoryFeignClient).releaseStock(any(InventoryReleaseRequest.class));
        verify(redisTemplate).delete("order:timeout:" + orderId);
        verify(couponFeignClient, never()).returnCoupon(any(CouponFeignClient.ReturnCouponRequest.class));
    }

    @Test
    void cancelOrder_WhenOrderNotFound_ShouldThrowBusinessException() {
        Long userId = 1L;
        Long orderId = 100L;

        when(orderMapper.selectById(orderId)).thenReturn(null);

        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("ORDER_NOT_FOUND");
                    assertThat(be.getMessage()).isEqualTo("订单不存在");
                });
    }

    @Test
    void cancelOrder_WhenNotOwner_ShouldThrowBusinessException() {
        Long userId = 1L;
        Long orderId = 100L;

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(999L);
        order.setStatus("PENDING_PAYMENT");

        when(orderMapper.selectById(orderId)).thenReturn(order);

        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("ORDER_ACCESS_DENIED");
                });
    }

    @Test
    void shipOrder_WhenOrderIsPaid_ShouldShipOrder() {
        Long orderId = 100L;
        Long userId = 1L;

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus("PAID");

        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrderId(orderId);
        orderItem.setSkuId(200L);
        orderItem.setQuantity(2);

        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO expectedDto = new OrderDTO(orderId, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"), BigDecimal.ZERO, null, "SHIPPED", "张三", "13800138000", "地址", null, null, null, null, List.of(itemDto), null, null);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(orderMapper.updateStatusAndShippedAtIfMatch(orderId, "PAID", "SHIPPED")).thenReturn(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orderItem));
        when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of(itemDto));
        when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(expectedDto);

        OrderDTO result = orderService.shipOrder(orderId);

        assertThat(result).isEqualTo(expectedDto);
        verify(orderMapper).updateStatusAndShippedAtIfMatch(orderId, "PAID", "SHIPPED");
        verify(orderEventProducer).sendOrderStatusChange(argThat(msg ->
                msg.orderId().equals(orderId)
                        && msg.userId().equals(userId)
                        && "PAID".equals(msg.oldStatus())
                        && "SHIPPED".equals(msg.newStatus())
        ));
    }

    @Test
    void confirmReceipt_WhenOrderIsShipped_ShouldCompleteOrder() {
        Long userId = 1L;
        Long orderId = 100L;

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus("SHIPPED");

        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrderId(orderId);
        orderItem.setSkuId(200L);
        orderItem.setQuantity(2);

        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO expectedDto = new OrderDTO(orderId, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"), BigDecimal.ZERO, null, "COMPLETED", "张三", "13800138000", "地址", null, null, null, null, List.of(itemDto), null, null);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(orderMapper.updateStatusAndCompletedAtIfMatch(orderId, "SHIPPED", "COMPLETED")).thenReturn(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orderItem));
        when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of(itemDto));
        when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(expectedDto);

        OrderDTO result = orderService.confirmReceipt(userId, orderId);

        assertThat(result).isEqualTo(expectedDto);
        verify(orderMapper).updateStatusAndCompletedAtIfMatch(orderId, "SHIPPED", "COMPLETED");
        verify(orderEventProducer).sendOrderStatusChange(argThat(msg ->
                msg.orderId().equals(orderId)
                        && msg.userId().equals(userId)
                        && "SHIPPED".equals(msg.oldStatus())
                        && "COMPLETED".equals(msg.newStatus())
        ));
    }

    @Test
    void requestRefund_WhenOrderIsPaid_ShouldSetRefunding() {
        Long userId = 1L;
        Long orderId = 100L;
        String refundReason = "商品有问题";

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus("PAID");

        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrderId(orderId);
        orderItem.setSkuId(200L);
        orderItem.setQuantity(2);

        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO expectedDto = new OrderDTO(orderId, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"), BigDecimal.ZERO, null, "REFUNDING", "张三", "13800138000", "地址", null, null, refundReason, null, List.of(itemDto), null, null);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(orderMapper.updateStatusToRefunding(orderId, "PAID", "REFUNDING", refundReason)).thenReturn(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orderItem));
        when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of(itemDto));
        when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(expectedDto);

        OrderDTO result = orderService.requestRefund(userId, orderId, refundReason);

        assertThat(result).isEqualTo(expectedDto);
        verify(orderMapper).updateStatusToRefunding(orderId, "PAID", "REFUNDING", refundReason);
        verify(orderEventProducer).sendOrderStatusChange(argThat(msg ->
                msg.orderId().equals(orderId)
                        && msg.userId().equals(userId)
                        && "PAID".equals(msg.oldStatus())
                        && "REFUNDING".equals(msg.newStatus())
        ));
    }

    @Test
    void approveRefund_WhenOrderIsRefunding_ShouldRefund() {
        Long orderId = 100L;
        Long userId = 1L;
        Long couponId = 50L;
        Long paymentId = 500L;

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(userId);
        order.setStatus("REFUNDING");
        order.setCouponId(couponId);

        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrderId(orderId);
        orderItem.setSkuId(200L);
        orderItem.setQuantity(2);

        PaymentFeignClient.PaymentDTO paymentDto = new PaymentFeignClient.PaymentDTO(
                paymentId, orderId, "PAY123", new BigDecimal("198.00"), "ALIPAY", "PAID",
                LocalDateTime.now(), LocalDateTime.now(), null
        );

        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO expectedDto = new OrderDTO(orderId, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"), BigDecimal.ZERO, couponId, "REFUNDED", "张三", "13800138000", "地址", null, null, null, null, List.of(itemDto), null, null);

        when(orderMapper.selectById(orderId)).thenReturn(order);
        when(paymentFeignClient.getPaymentByOrderId(orderId)).thenReturn(ApiResponse.ok(paymentDto));
        when(paymentFeignClient.refund(paymentId)).thenReturn(ApiResponse.ok(paymentDto));
        when(orderMapper.updateStatusToRefunded(orderId, "REFUNDING", "REFUNDED")).thenReturn(1);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orderItem));
        when(inventoryFeignClient.releaseStock(any(InventoryReleaseRequest.class))).thenReturn(ApiResponse.ok(null));
        when(couponFeignClient.returnCoupon(any(CouponFeignClient.ReturnCouponRequest.class))).thenReturn(ApiResponse.ok(null));
        when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of(itemDto));
        when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(expectedDto);

        OrderDTO result = orderService.approveRefund(orderId);

        assertThat(result).isEqualTo(expectedDto);
        verify(paymentFeignClient).refund(paymentId);
        verify(orderMapper).updateStatusToRefunded(orderId, "REFUNDING", "REFUNDED");
        verify(inventoryFeignClient).releaseStock(any(InventoryReleaseRequest.class));
        verify(couponFeignClient).returnCoupon(any(CouponFeignClient.ReturnCouponRequest.class));
        verify(orderEventProducer).sendOrderStatusChange(argThat(msg ->
                msg.orderId().equals(orderId)
                        && "REFUNDING".equals(msg.oldStatus())
                        && "REFUNDED".equals(msg.newStatus())
        ));
    }

    @Test
    void createOrder_WithValidRequest_ShouldCreateOrder() {
        Long userId = 1L;

        CreateOrderRequest.OrderItemInput itemInput = new CreateOrderRequest.OrderItemInput(
                300L, 200L, 2, "商品A", "img.jpg", "红色", new BigDecimal("99.00")
        );
        CreateOrderRequest request = new CreateOrderRequest(
                "req-001", List.of(itemInput), "张三", "13800138000", "地址", null, null
        );

        OrderItem orderItem = new OrderItem();
        orderItem.setId(10L);
        orderItem.setOrderId(1L);
        orderItem.setSkuId(200L);
        orderItem.setQuantity(2);

        OrderItemDTO itemDto = new OrderItemDTO(10L, 300L, 200L, "商品A", "img.jpg", "红色", new BigDecimal("99.00"), 2);
        OrderDTO expectedDto = new OrderDTO(1L, "ORD123", new BigDecimal("198.00"), new BigDecimal("198.00"), BigDecimal.ZERO, null, "PENDING_PAYMENT", "张三", "13800138000", "地址", null, null, null, null, List.of(itemDto), null, null);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(inventoryFeignClient.deductStock(any(InventoryDeductRequest.class))).thenReturn(ApiResponse.ok(true));
        when(orderMapper.insert(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(1L);
            return 1;
        });
        when(orderItemMapper.insert(any(OrderItem.class))).thenReturn(1);
        when(cartFeignClient.clearCheckedItems(userId)).thenReturn(ApiResponse.ok(null));
        when(orderEventProducer.sendOrderTimeoutCheck(anyString())).thenReturn(true);
        when(orderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(orderItem));
        when(orderConverter.toItemDTOList(anyList())).thenReturn(List.of(itemDto));
        when(orderConverter.toDTO(any(Order.class), anyList())).thenReturn(expectedDto);

        OrderDTO result = orderService.createOrder(userId, request);

        assertThat(result).isEqualTo(expectedDto);
        verify(inventoryFeignClient).deductStock(any(InventoryDeductRequest.class));
        verify(orderMapper).insert(any(Order.class));
        verify(orderItemMapper).insert(any(OrderItem.class));
        verify(valueOperations).set(eq("order:timeout:1"), eq("1"), eq(Duration.ofMinutes(15)));
        verify(orderEventProducer).sendOrderTimeoutCheck(anyString());
    }
}
