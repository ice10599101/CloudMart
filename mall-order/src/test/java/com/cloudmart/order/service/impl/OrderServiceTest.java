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
import com.cloudmart.common.async.compensation.CompensationTaskService;
import com.cloudmart.common.async.outbox.OutboxService;
import tools.jackson.databind.ObjectMapper;
import com.cloudmart.common.async.EventEnvelope;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;
    private OrderConverter orderConverter;
    private InventoryFeignClient inventoryFeignClient;
    private com.cloudmart.order.feign.WmsShippingFeignClient wmsShippingFeignClient;
    private com.cloudmart.order.feign.RiskFeignClient riskFeignClient;
    private com.cloudmart.order.feign.ProductFeignClient productFeignClient;
    private CartFeignClient cartFeignClient;
    private CouponFeignClient couponFeignClient;
    private com.cloudmart.order.feign.RefundFeignClient refundFeignClient;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OrderEventProducer orderEventProducer;
    private OutboxService outboxService = mock(OutboxService.class);
    private CompensationTaskService compensationTaskService = mock(CompensationTaskService.class);
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderMapper = mock(OrderMapper.class);
        orderItemMapper = mock(OrderItemMapper.class);
        orderConverter = mock(OrderConverter.class);
        inventoryFeignClient = mock(InventoryFeignClient.class);
        productFeignClient = mock(com.cloudmart.order.feign.ProductFeignClient.class);
        wmsShippingFeignClient = mock(com.cloudmart.order.feign.WmsShippingFeignClient.class);
        riskFeignClient = mock(com.cloudmart.order.feign.RiskFeignClient.class);
        cartFeignClient = mock(CartFeignClient.class);
        couponFeignClient = mock(CouponFeignClient.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        orderEventProducer = mock(OrderEventProducer.class);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        refundFeignClient = mock(com.cloudmart.order.feign.RefundFeignClient.class);
        lenient().when(refundFeignClient.createRefund(any())).thenReturn(com.cloudmart.common.api.ApiResponse.ok(
                java.util.Map.of("refundNo", "RF100", "status", "SUCCEEDED")));

        orderService = new OrderServiceImpl(
                orderMapper, orderItemMapper, orderConverter,
                inventoryFeignClient, cartFeignClient,
                couponFeignClient,
                refundFeignClient,
                new OrderCouponPolicy(couponFeignClient),
                productFeignClient,
                riskFeignClient,
                wmsShippingFeignClient,
                redisTemplate, orderEventProducer,
                outboxService, compensationTaskService, new ObjectMapper(),
                org.mockito.Mockito.mock(com.cloudmart.order.repository.OrderQuoteMapper.class),
                org.mockito.Mockito.mock(com.cloudmart.order.repository.OrderQuoteItemMapper.class),
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class)
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
        verify(outboxService).record(argThat(evt ->
                "ORDER_STATUS_CHANGE".equals(evt.eventType())
                        && evt.aggregateId().equals(String.valueOf(orderId))
                        && evt.payload().contains("\"oldStatus\":\"PENDING_PAYMENT\"")
                        && evt.payload().contains("\"newStatus\":\"CANCELLED\"")
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
        // WMS-01 闭环：建包裹 + 出库 stub
        when(wmsShippingFeignClient.createShipping(any())).thenReturn(
                ApiResponse.ok(java.util.Map.of("id", 55L, "orderId", orderId)));
        when(wmsShippingFeignClient.updateStatus(55L, "SHIPPED")).thenReturn(
                ApiResponse.ok(java.util.Map.of("id", 55L, "status", "SHIPPED")));

        OrderDTO result = orderService.shipOrder(orderId, "顺丰", "SF1234567890", 10L);

        assertThat(result).isEqualTo(expectedDto);
        verify(orderMapper).updateStatusAndShippedAtIfMatch(orderId, "PAID", "SHIPPED");
        verify(wmsShippingFeignClient).createShipping(any());
        verify(outboxService).record(argThat(evt ->
                "ORDER_STATUS_CHANGE".equals(evt.eventType())
                        && evt.aggregateId().equals(String.valueOf(orderId))
                        && evt.payload().contains("\"oldStatus\":\"PAID\"")
                        && evt.payload().contains("\"newStatus\":\"SHIPPED\"")
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
        verify(outboxService).record(argThat(evt ->
                "ORDER_STATUS_CHANGE".equals(evt.eventType())
                        && evt.aggregateId().equals(String.valueOf(orderId))
                        && evt.payload().contains("\"oldStatus\":\"SHIPPED\"")
                        && evt.payload().contains("\"newStatus\":\"COMPLETED\"")
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
        verify(outboxService).record(argThat(evt ->
                "ORDER_STATUS_CHANGE".equals(evt.eventType())
                        && evt.aggregateId().equals(String.valueOf(orderId))
                        && evt.payload().contains("\"oldStatus\":\"PAID\"")
                        && evt.payload().contains("\"newStatus\":\"REFUNDING\"")
        ));
    }

    @Test
    void approveRefund_ChannelUnavailable_ShouldThrowAndNotRefund() {
        Long orderId = 100L;

        Order order = new Order();
        order.setId(orderId);
        order.setUserId(1L);
        order.setStatus("REFUNDING");
        when(orderMapper.selectById(orderId)).thenReturn(order);

        // T02/QA06：支付服务不可用（拒绝型 fallback 抛 REFUND_SERVICE_UNAVAILABLE）时审批不上推进订单
        when(refundFeignClient.createRefund(any())).thenThrow(
                new BusinessException("REFUND_SERVICE_UNAVAILABLE", "退款服务不可用，请稍后重试"));
        assertThatThrownBy(() -> orderService.approveRefund(orderId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("REFUND_SERVICE_UNAVAILABLE"));
        verify(orderMapper, never()).updateStatusToRefunded(anyLong(), anyString(), anyString());
        verify(outboxService, never()).record(any(EventEnvelope.class));
    }

    @Test
    void createOrder_WithValidRequest_ShouldCreateOrder() {
        Long userId = 1L;

        CreateOrderRequest.OrderItemInput itemInput = new CreateOrderRequest.OrderItemInput(
                300L, 200L, 2, "商品A", "img.jpg", "红色", new BigDecimal("99.00")
        );
        CreateOrderRequest request = new CreateOrderRequest(
                "req-001", List.of(itemInput), "张三", "13800138000", "地址", null, null, null
        );

        // RISK-01：风控放行
        when(riskFeignClient.check(any())).thenReturn(ApiResponse.ok(java.util.Map.of("result", "PASS")));

        // TRADE-01：下单前服务端按 skuId 覆盖权威价格/商品信息
        Map<String, Object> authoritativeSku = new HashMap<>();
        authoritativeSku.put("skuId", 200L);
        authoritativeSku.put("productId", 300L);
        authoritativeSku.put("productName", "商品A");
        authoritativeSku.put("image", "img.jpg");
        authoritativeSku.put("attributes", "红色");
        authoritativeSku.put("price", new BigDecimal("99.00"));
        authoritativeSku.put("status", 1);
        when(productFeignClient.getSkusBatch(List.of(200L)))
                .thenReturn(ApiResponse.ok(List.of(authoritativeSku)));

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
