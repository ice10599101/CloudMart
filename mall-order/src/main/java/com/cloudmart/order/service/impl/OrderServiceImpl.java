package com.cloudmart.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.common.exception.BusinessException;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.apache.seata.spring.annotation.GlobalTransactional;

import com.cloudmart.order.converter.OrderConverter;
import com.cloudmart.order.dto.CreateOrderRequest;
import com.cloudmart.order.dto.OrderDTO;
import com.cloudmart.order.dto.OrderTodayStatsResponse;
import com.cloudmart.order.entity.Order;
import com.cloudmart.order.entity.OrderItem;
import com.cloudmart.order.dto.InventoryDeductRequest;
import com.cloudmart.order.dto.InventoryReleaseRequest;
import com.cloudmart.order.feign.CartFeignClient;
import com.cloudmart.order.feign.CouponFeignClient;
import com.cloudmart.order.feign.InventoryFeignClient;
import com.cloudmart.order.feign.PaymentFeignClient;
import com.cloudmart.order.feign.CouponFeignClient.UseCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient.ReturnCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO;
import com.cloudmart.order.feign.PaymentFeignClient.CreatePaymentRequest;
import com.cloudmart.order.feign.PaymentFeignClient.PaymentDTO;
import com.cloudmart.common.async.EventEnvelope;
import com.cloudmart.common.async.compensation.CompensationTaskService;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.order.mq.OrderEventProducer;
import tools.jackson.databind.ObjectMapper;
import com.cloudmart.order.mq.OrderStatusChangeMessage;
import com.cloudmart.order.repository.OrderItemMapper;
import com.cloudmart.order.repository.OrderMapper;
import com.cloudmart.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final String ORDER_TIMEOUT_KEY_PREFIX = "order:timeout:";
    private static final Duration ORDER_TIMEOUT = Duration.ofMinutes(15);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderConverter orderConverter;
    private final InventoryFeignClient inventoryFeignClient;
    private final CartFeignClient cartFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final CouponFeignClient couponFeignClient;
    private final StringRedisTemplate redisTemplate;
    private final OrderEventProducer orderEventProducer;
    private final OutboxService outboxService;
    private final CompensationTaskService compensationTaskService;
    private final ObjectMapper objectMapper;

    @Override
    @SentinelResource(value = "createOrder", blockHandler = "createOrderBlockHandler", fallback = "createOrderFallback")
    @GlobalTransactional(timeoutMills = 30000, name = "createOrder")
    @Transactional
    public OrderDTO createOrder(Long userId, CreateOrderRequest request) {
        String idempotentKey = "order:idempotent:" + request.requestId();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(idempotentKey, "1", Duration.ofMinutes(30));
        if (Boolean.FALSE.equals(acquired)) {
            throw new BusinessException("DUPLICATE_REQUEST", "Duplicate order request");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException("ORDER_EMPTY", "订单项不能为空");
        }

        List<CreateOrderRequest.OrderItemInput> deductedItems = new ArrayList<>();
        try {
            for (CreateOrderRequest.OrderItemInput item : request.items()) {
                InventoryDeductRequest deductReq = new InventoryDeductRequest(item.skuId(), item.quantity(), 0L);
                ApiResponse<Boolean> deductResult = inventoryFeignClient.deductStock(deductReq);
                if (deductResult == null || !Boolean.TRUE.equals(deductResult.data())) {
                    throw new BusinessException("STOCK_INSUFFICIENT", "商品库存不足: SKU " + item.skuId());
                }
                deductedItems.add(item);
            }
        } catch (BusinessException e) {
            compensateDeductedStock(deductedItems);
            throw e;
        } catch (Exception e) {
            compensateDeductedStock(deductedItems);
            throw new BusinessException("STOCK_DEDUCT_FAILED", "库存扣减失败，请重试");
        }

        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(generateOrderNo());
        order.setStatus("PENDING_PAYMENT");
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemInput item : request.items()) {
            totalAmount = totalAmount.add(item.price().multiply(BigDecimal.valueOf(item.quantity())));
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        UserCouponDTO validatedCoupon = null;
        if (request.couponId() != null) {
            validatedCoupon = validateAndGetCoupon(request.couponId(), userId, totalAmount);
            discountAmount = calculateDiscount(validatedCoupon, totalAmount);
        }

        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(totalAmount.subtract(discountAmount));
        order.setCouponId(request.couponId());
        order.setActivityId(request.activityId());

        orderMapper.insert(order);

        if (validatedCoupon != null) {
            try {
                UseCouponRequest useReq = new UseCouponRequest(request.couponId(), order.getId());
                ApiResponse<Void> useResult = couponFeignClient.useCoupon(useReq);
                if (useResult == null || !useResult.success()) {
                    throw new BusinessException("COUPON_USE_FAILED", "优惠券使用失败");
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("COUPON_USE_FAILED", "优惠券使用失败");
            }
        }

        for (CreateOrderRequest.OrderItemInput item : request.items()) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setProductId(item.productId());
            orderItem.setSkuId(item.skuId());
            orderItem.setProductName(item.productName());
            orderItem.setSkuImage(item.skuImage());
            orderItem.setSkuAttributes(item.skuAttributes());
            orderItem.setPrice(item.price());
            orderItem.setQuantity(item.quantity());
            orderItemMapper.insert(orderItem);
        }

        try {
            cartFeignClient.clearCheckedItems(userId);
        } catch (Exception e) {
            log.warn("清空购物车已选商品失败, userId={}: {}", userId, e.getMessage());
        }

        redisTemplate.opsForValue().set(
                ORDER_TIMEOUT_KEY_PREFIX + order.getId(),
                String.valueOf(order.getId()),
                ORDER_TIMEOUT
        );

        orderEventProducer.sendOrderTimeoutCheck(order.getOrderNo());

        List<OrderItem> orderItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId())
        );
        return orderConverter.toDTO(order, orderConverter.toItemDTOList(orderItems));
    }

    @Override
    @SentinelResource(value = "cancelOrder", blockHandler = "cancelOrderBlockHandler")
    @Transactional
    public OrderDTO cancelOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作此订单");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许取消");
        }

        int updated = orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "CANCELLED");
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, userId, "PENDING_PAYMENT", "CANCELLED"
        ));

        releaseStockForOrder(orderId);

        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), orderId);
        }

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + orderId);

        Order cancelledOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(cancelledOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    @Transactional
    public void notifyPaymentSuccess(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return;
        }

        int updated = orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "PAID");
        if (updated == 0) {
            log.warn("订单支付状态更新失败，可能已被取消, orderId={}", orderId);
            return;
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PENDING_PAYMENT", "PAID"
        ));

        confirmStockDeduct(orderId);

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + orderId);
    }

    @Override
    @Transactional
    public void markOrderPaid(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            log.warn("markOrderPaid: 订单不存在, orderId={}", orderId);
            return;
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return;
        }

        int updated = orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "PAID");
        if (updated == 0) {
            log.warn("markOrderPaid: 订单支付状态更新失败，可能已被取消, orderId={}", orderId);
            return;
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PENDING_PAYMENT", "PAID"
        ));

        confirmStockDeduct(orderId);

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + orderId);
    }

    @Override
    @Transactional
    public void notifyOrderCancel(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return;
        }

        int updated = orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "CANCELLED");
        if (updated == 0) {
            log.info("订单已不是待支付状态，跳过取消, orderId={}", orderId);
            return;
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PENDING_PAYMENT", "CANCELLED"
        ));

        releaseStockForOrder(orderId);

        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), orderId);
        }

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + orderId);
    }

    @Override
    public ApiResponse<List<OrderDTO>> listOrders(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(status != null && !status.isEmpty(), Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);

        Page<Order> orderPage = orderMapper.selectPage(new Page<Order>(page, size), wrapper);

        List<Long> orderIds = orderPage.getRecords().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = Map.of();
        if (!orderIds.isEmpty()) {
            List<OrderItem> allItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds)
            );
            itemsByOrderId = allItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        }

        Map<Long, List<OrderItem>> finalItemsByOrderId = itemsByOrderId;
        List<OrderDTO> dtos = orderPage.getRecords().stream().map(order -> {
            List<OrderItem> items = finalItemsByOrderId.getOrDefault(order.getId(), List.of());
            return orderConverter.toDTO(order, orderConverter.toItemDTOList(items));
        }).toList();

        Meta meta = new Meta(page, size, orderPage.getTotal());
        return ApiResponse.ok(dtos, meta);
    }

    @Override
    public OrderDTO getOrderById(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权查看此订单");
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(order, orderConverter.toItemDTOList(items));
    }

    @Override
    public PaymentDTO payForOrder(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作此订单");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许支付");
        }

        CreatePaymentRequest paymentReq = new CreatePaymentRequest(orderId, order.getPayAmount(), null);
        ApiResponse<PaymentDTO> result = paymentFeignClient.createPayment(paymentReq);
        if (result == null || result.data() == null) {
            throw new BusinessException("PAYMENT_CREATE_FAILED", "创建支付记录失败");
        }
        return result.data();
    }

    @Override
    public PaymentDTO getPaymentByOrderId(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权查看此订单支付信息");
        }

        ApiResponse<PaymentDTO> result = paymentFeignClient.getPaymentByOrderId(orderId);
        if (result == null || result.data() == null) {
            throw new BusinessException("PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        return result.data();
    }

    @Override
    @Transactional
    public OrderDTO shipOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许发货");
        }

        int updated = orderMapper.updateStatusAndShippedAtIfMatch(orderId, "PAID", "SHIPPED");
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PAID", "SHIPPED"
        ));

        Order shippedOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(shippedOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    @Transactional
    public OrderDTO confirmReceipt(Long userId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作此订单");
        }
        if (!"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许确认收货");
        }

        int updated = orderMapper.updateStatusAndCompletedAtIfMatch(orderId, "SHIPPED", "COMPLETED");
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, userId, "SHIPPED", "COMPLETED"
        ));

        Order completedOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(completedOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    @Transactional
    public OrderDTO requestRefund(Long userId, Long orderId, String refundReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("ORDER_ACCESS_DENIED", "无权操作此订单");
        }
        if (!"PAID".equals(order.getStatus()) && !"SHIPPED".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许申请退款");
        }

        String previousStatus = order.getStatus();
        int updated = orderMapper.updateStatusToRefunding(orderId, previousStatus, "REFUNDING", refundReason);
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, userId, previousStatus, "REFUNDING"
        ));

        Order refundingOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(refundingOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    @Transactional
    public OrderDTO approveRefund(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"REFUNDING".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许审批退款");
        }

        PaymentDTO payment = null;
        try {
            ApiResponse<PaymentDTO> paymentResp = paymentFeignClient.getPaymentByOrderId(orderId);
            if (paymentResp != null && paymentResp.data() != null) {
                payment = paymentResp.data();
            }
        } catch (Exception e) {
            log.warn("查询支付记录失败, orderId={}: {}", orderId, e.getMessage());
        }

        if (payment != null) {
            try {
                paymentFeignClient.refund(payment.id());
            } catch (Exception e) {
                throw new BusinessException("PAYMENT_REFUND_FAILED", "退款失败: " + e.getMessage());
            }
        }

        int updated = orderMapper.updateStatusToRefunded(orderId, "REFUNDING", "REFUNDED");
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "REFUNDING", "REFUNDED"
        ));

        releaseStockForOrder(orderId);

        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), orderId);
        }

        Order refundedOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(refundedOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    @Transactional
    public OrderDTO rejectRefund(Long orderId, String rejectReason) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"REFUNDING".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许拒绝退款");
        }

        String previousStatus = "PAID";
        int updated = orderMapper.updateStatusRejectRefund(orderId, "REFUNDING", previousStatus, rejectReason);
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "REFUNDING", previousStatus
        ));

        Order rejectedOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(rejectedOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    public ApiResponse<List<OrderDTO>> listAllOrders(String status, Long userId, String orderNo, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(status != null && !status.isEmpty(), Order::getStatus, status)
                .eq(userId != null, Order::getUserId, userId)
                .like(orderNo != null && !orderNo.isEmpty(), Order::getOrderNo, orderNo)
                .orderByDesc(Order::getCreatedAt);

        Page<Order> orderPage = orderMapper.selectPage(new Page<>(page, size), wrapper);

        List<Long> orderIds = orderPage.getRecords().stream().map(Order::getId).toList();
        Map<Long, List<OrderItem>> itemsByOrderId = Map.of();
        if (!orderIds.isEmpty()) {
            List<OrderItem> allItems = orderItemMapper.selectList(
                    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds)
            );
            itemsByOrderId = allItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId));
        }

        Map<Long, List<OrderItem>> finalItemsByOrderId = itemsByOrderId;
        List<OrderDTO> dtos = orderPage.getRecords().stream().map(order -> {
            List<OrderItem> items = finalItemsByOrderId.getOrDefault(order.getId(), List.of());
            return orderConverter.toDTO(order, orderConverter.toItemDTOList(items));
        }).toList();

        Meta meta = new Meta(page, size, orderPage.getTotal());
        return ApiResponse.ok(dtos, meta);
    }

    @Override
    public OrderDTO getAdminOrderById(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(order, orderConverter.toItemDTOList(items));
    }

    @Override
    @Transactional
    public OrderDTO adminCancelOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许取消");
        }

        int updated = orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "CANCELLED");
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PENDING_PAYMENT", "CANCELLED"
        ));

        releaseStockForOrder(orderId);

        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), orderId);
        }

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + orderId);

        Order cancelledOrder = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(cancelledOrder, orderConverter.toItemDTOList(items));
    }

    @Override
    public ApiResponse<OrderTodayStatsResponse> getTodayStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(LocalTime.MAX);

        LambdaQueryWrapper<Order> countWrapper = new LambdaQueryWrapper<Order>()
                .ge(Order::getCreatedAt, startOfDay)
                .le(Order::getCreatedAt, endOfDay);
        long todayOrderCount = orderMapper.selectCount(countWrapper);

        List<String> paidStatuses = List.of("PAID", "SHIPPED", "COMPLETED");
        LambdaQueryWrapper<Order> revenueWrapper = new LambdaQueryWrapper<Order>()
                .ge(Order::getCreatedAt, startOfDay)
                .le(Order::getCreatedAt, endOfDay)
                .in(Order::getStatus, paidStatuses);
        List<Order> paidOrders = orderMapper.selectList(revenueWrapper);
        BigDecimal todayRevenue = paidOrders.stream()
                .map(Order::getPayAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ApiResponse.ok(new OrderTodayStatsResponse(todayOrderCount, todayRevenue));
    }

    private void confirmStockDeduct(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        for (OrderItem item : items) {
            try {
                inventoryFeignClient.confirmDeduct(item.getSkuId(), item.getQuantity(), orderId);
            } catch (Exception e) {
                // ASYNC-01：失败登记持久化补偿任务（指数退避重试），替代仅记日志
                log.error("确认库存扣减失败, 已登记补偿任务, skuId={}, orderId={}: {}", item.getSkuId(), orderId, e.getMessage());
                compensationTaskService.createIfAbsent(
                        "stock-confirm:" + orderId + ":" + item.getSkuId(), "stock-confirm",
                        String.valueOf(orderId), compensationPayload(orderId, item.getSkuId(), item.getQuantity()));
            }
        }
    }

    private void compensateDeductedStock(List<CreateOrderRequest.OrderItemInput> deductedItems) {
        for (CreateOrderRequest.OrderItemInput item : deductedItems) {
            try {
                InventoryReleaseRequest releaseReq = new InventoryReleaseRequest(item.skuId(), item.quantity(), 0L);
                inventoryFeignClient.releaseStock(releaseReq);
            } catch (Exception ex) {
                // ASYNC-01：失败登记持久化补偿任务（orderId 未知，以 0 占位并由库存侧按需核对）
                log.error("补偿释放库存失败, 已登记补偿任务, skuId={}: {}", item.skuId(), ex.getMessage());
                compensationTaskService.createIfAbsent(
                        "stock-release-create:" + item.skuId() + ":" + UUID.randomUUID().toString().substring(0, 8),
                        "stock-release", "0", compensationPayload(0L, item.skuId(), item.quantity()));
            }
        }
    }

    private void releaseStockForOrder(Long orderId) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        for (OrderItem item : items) {
            try {
                InventoryReleaseRequest releaseReq = new InventoryReleaseRequest(item.getSkuId(), item.getQuantity(), orderId);
                inventoryFeignClient.releaseStock(releaseReq);
            } catch (Exception e) {
                // ASYNC-01：失败登记持久化补偿任务
                log.error("释放库存失败, 已登记补偿任务, skuId={}, orderId={}: {}", item.getSkuId(), orderId, e.getMessage());
                compensationTaskService.createIfAbsent(
                        "stock-release:" + orderId + ":" + item.getSkuId(), "stock-release",
                        String.valueOf(orderId), compensationPayload(orderId, item.getSkuId(), item.getQuantity()));
            }
        }
    }

    private String generateOrderNo() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }

    private UserCouponDTO validateAndGetCoupon(Long couponId, Long userId, BigDecimal totalAmount) {
        ApiResponse<UserCouponDTO> couponResp = couponFeignClient.getCouponById(couponId);
        if (couponResp == null || !couponResp.success() || couponResp.data() == null) {
            throw new BusinessException("COUPON_NOT_FOUND", "优惠券不存在");
        }
        UserCouponDTO coupon = couponResp.data();
        if (!coupon.userId().equals(userId)) {
            throw new BusinessException("COUPON_ACCESS_DENIED", "无权使用此优惠券");
        }
        if (!"UNUSED".equals(coupon.status())) {
            throw new BusinessException("COUPON_ALREADY_USED", "优惠券已使用");
        }
        if (coupon.thresholdAmount() != null && totalAmount.compareTo(coupon.thresholdAmount()) < 0) {
            throw new BusinessException("COUPON_THRESHOLD_NOT_MET", "未达优惠券使用门槛");
        }
        return coupon;
    }

    private BigDecimal calculateDiscount(UserCouponDTO coupon, BigDecimal totalAmount) {
        if ("AMOUNT_OFF".equals(coupon.templateType()) && coupon.discountAmount() != null) {
            return coupon.discountAmount();
        }
        if ("PERCENT_OFF".equals(coupon.templateType()) && coupon.discountRate() != null) {
            BigDecimal discounted = totalAmount.multiply(coupon.discountRate());
            return totalAmount.subtract(discounted);
        }
        return BigDecimal.ZERO;
    }

    private void returnCouponForOrder(Long couponId, Long orderId) {
        try {
            ReturnCouponRequest returnReq = new ReturnCouponRequest(couponId, orderId);
            couponFeignClient.returnCoupon(returnReq);
        } catch (Exception e) {
            // ASYNC-01：失败登记持久化补偿任务
            log.error("退回优惠券失败, 已登记补偿任务, couponId={}, orderId={}: {}", couponId, orderId, e.getMessage());
            compensationTaskService.createIfAbsent(
                    "coupon-return:" + orderId + ":" + couponId, "coupon-return",
                    String.valueOf(orderId),
                    compensationJson(java.util.Map.of("orderId", orderId, "couponId", couponId)));
        }
    }

    /** ASYNC-01：库存补偿任务载荷 */
    private String compensationPayload(Long orderId, Long skuId, Integer quantity) {
        return compensationJson(java.util.Map.of("orderId", orderId, "skuId", skuId, "quantity", quantity));
    }

    private String compensationJson(java.util.Map<String, ?> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("compensation payload serialize failed", e);
        }
    }

    /**
     * ASYNC-01：订单状态事件经 Outbox 可靠投递（与业务状态变更同事务登记），
     * 替代直发 MQ 且吞异常的旧路径。
     */
    private void publishOutboxEvent(OrderStatusChangeMessage message) {
        outboxService.record(EventEnvelope.of("ORDER_STATUS_CHANGE", 1,
                String.valueOf(message.orderId()), 0, null,
                compensationJson(java.util.Map.of(
                        "orderId", message.orderId(),
                        "userId", message.userId(),
                        "oldStatus", message.oldStatus(),
                        "newStatus", message.newStatus()))));
    }

    @Override
    @Transactional
    public void cancelTimeoutOrder(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );
        if (order == null) {
            log.warn("Timeout order not found, orderNo={}", orderNo);
            return;
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            log.info("Order is no longer pending payment, skip cancel, orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }

        int updated = orderMapper.updateStatusIfMatch(order.getId(), "PENDING_PAYMENT", "CANCELLED");
        if (updated == 0) {
            log.info("Order status changed before timeout cancel, orderNo={}", orderNo);
            return;
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                order.getId(), order.getUserId(), "PENDING_PAYMENT", "CANCELLED"
        ));

        releaseStockForOrder(order.getId());

        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), order.getId());
        }

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + order.getId());

        log.info("Timeout order cancelled, orderNo={}", orderNo);
    }

    public OrderDTO createOrderBlockHandler(Long userId, CreateOrderRequest request, BlockException ex) {
        log.warn("createOrder blocked by Sentinel: {}", ex.getRule());
        return null;
    }

    public OrderDTO createOrderFallback(Long userId, CreateOrderRequest request, Throwable throwable) {
        log.warn("createOrder fallback triggered: {}", throwable.getMessage());
        return null;
    }

    public OrderDTO cancelOrderBlockHandler(Long userId, Long orderId, BlockException ex) {
        log.warn("cancelOrder blocked by Sentinel: {}", ex.getRule());
        return null;
    }
}
