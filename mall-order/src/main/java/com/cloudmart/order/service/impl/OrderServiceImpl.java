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
import com.cloudmart.order.feign.CouponFeignClient.UseCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient.ReturnCouponRequest;
import com.cloudmart.order.feign.CouponFeignClient.UserCouponDTO;
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
    private final CouponFeignClient couponFeignClient;
    private final com.cloudmart.order.feign.RefundFeignClient refundFeignClient;
    private final com.cloudmart.order.feign.ProductFeignClient productFeignClient;
    private final com.cloudmart.order.feign.RiskFeignClient riskFeignClient;
    private final com.cloudmart.order.feign.WmsShippingFeignClient wmsShippingFeignClient;
    private final StringRedisTemplate redisTemplate;
    private final OrderEventProducer orderEventProducer;
    private final OutboxService outboxService;
    private final CompensationTaskService compensationTaskService;
    private final ObjectMapper objectMapper;
    private final com.cloudmart.order.repository.OrderQuoteMapper orderQuoteMapper;
    private final com.cloudmart.order.repository.OrderQuoteItemMapper orderQuoteItemMapper;
    /** 自代理：createOrderFromQuote 经代理调用 createOrder，保证其事务/Seata 注解生效 */
    private final org.springframework.beans.factory.ObjectProvider<OrderService> selfProvider;

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

        // RISK-01：下单前置风控检查（黑名单/频次规则）——REJECT 拒绝下单，
        // 风控不可用时降级工厂抛错（fail-closed，"风控挂了就放行"被禁止）
        performRiskCheck(userId, "ORDER_CREATE");

        // TRADE-01：旧结算入口的价格丢弃适配——客户端声明的价格/商品名/图片/属性
        // 一律覆盖为商品服务权威值（T07：篡改 price/productId/skuId 不能改变服务端应付价）
        request = new CreateOrderRequest(request.requestId(),
                overrideItemsFromProduct(request.items()),
                request.receiverName(), request.receiverPhone(), request.receiverAddress(),
                request.couponId(), request.activityId());

        List<CreateOrderRequest.OrderItemInput> deductedItems = new ArrayList<>();

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
        // COUPON-01：应付金额下限保护，禁止负数订单
        order.setPayAmount(totalAmount.subtract(discountAmount).max(BigDecimal.ZERO));
        order.setCouponId(request.couponId());
        order.setActivityId(request.activityId());

        orderMapper.insert(order);

        // T04/T03：先落订单拿到真实 orderId，再跨服务预占——预占台账 (order_id, sku_id) 必须
        // 绑定真实订单事实（旧实现传 0L，预占不入账本，释放/确认走裸更新兼容分支）
        // SKU 按 ID 升序预占，保证并发订单的加锁顺序一致
        List<CreateOrderRequest.OrderItemInput> orderedItems = request.items().stream()
                .sorted(java.util.Comparator.comparingLong(CreateOrderRequest.OrderItemInput::skuId))
                .toList();
        List<Long> insufficientSkuIds = new ArrayList<>();
        try {
            for (CreateOrderRequest.OrderItemInput item : orderedItems) {
                InventoryDeductRequest deductReq = new InventoryDeductRequest(
                        item.skuId(), item.quantity(), order.getId());
                ApiResponse<Boolean> deductResult = inventoryFeignClient.deductStock(deductReq);
                if (deductResult == null || !Boolean.TRUE.equals(deductResult.data())) {
                    // 不中断循环：收集全部缺货 SKU 一次性返回，已成功预占的行随后统一补偿
                    insufficientSkuIds.add(item.skuId());
                    continue;
                }
                deductedItems.add(item);
            }
            if (!insufficientSkuIds.isEmpty()) {
                throw new BusinessException("STOCK_INSUFFICIENT",
                        "商品库存不足: SKU " + insufficientSkuIds);
            }
        } catch (BusinessException e) {
            compensateDeductedStock(deductedItems, order.getId());
            throw e;
        } catch (Exception e) {
            compensateDeductedStock(deductedItems, order.getId());
            throw new BusinessException("STOCK_DEDUCT_FAILED", "库存扣减失败，请重试");
        }

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

    /**
     * T05：支付成功唯一推进入口（T05 合并原 notifyPaymentSuccess/markOrderPaid 双路径）。
     * 校验支付事件金额/币种与订单应付一致（渠道事实核对），CAS PENDING_PAYMENT→PAID，
     * 订单 Outbox（ORDER_STATUS_CHANGE + ORDER_PAID——WMS 拣货单触发）+ 库存确认 + 超时键
     * 清理同一本地事务；已推进（PAID+）幂等跳过；已取消订单的迟到支付记 LATE_PAYMENT_DETECTED
     * 供补偿/对账处置，不静默丢弃（QA05）。
     */
    @Override
    @Transactional
    public void applyPaymentSucceeded(Long orderId, String expectedPayAmount, String currency) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            // 明确失败：订单不存在是真实异常，由消费者 failConsume 重试/进入死信，不假装成功
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在: " + orderId);
        }
        // 金额/币种校验（QA03/金额不匹配拒绝推进）：支付事件为渠道事实，订单应付为权威应付
        if (expectedPayAmount != null
                && order.getPayAmount() != null
                && order.getPayAmount().compareTo(new java.math.BigDecimal(expectedPayAmount)) != 0) {
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH",
                    "支付金额与订单应付不一致, orderId=" + orderId + ", expected=" + order.getPayAmount()
                            + ", paid=" + expectedPayAmount);
        }
        if (currency != null && !"CNY".equals(currency)) {
            throw new BusinessException("PAYMENT_CURRENCY_MISMATCH",
                    "支付币种不支持: " + currency + ", orderId=" + orderId);
        }
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            if ("CANCELLED".equals(order.getStatus())) {
                // QA05：订单取消后收到真实成功支付——进入 LATE_PAYMENT 处置（触发退款核查），不静默丢弃
                log.error("[T05] 迟到支付：订单已取消但收到支付成功, orderId={}, amount={}",
                        orderId, expectedPayAmount);
                outboxService.record(EventEnvelope.of("LATE_PAYMENT_DETECTED", 1,
                        String.valueOf(orderId), 1, null,
                        compensationJson(java.util.Map.of(
                                "orderId", orderId,
                                "userId", order.getUserId(),
                                "paidAmount", String.valueOf(expectedPayAmount)))));
                return;
            }
            // PAID 及之后状态：事件重放幂等跳过
            log.info("支付成功事件幂等跳过（订单状态 {}）, orderId={}", order.getStatus(), orderId);
            return;
        }

        int updated = orderMapper.updateStatusIfMatch(orderId, "PENDING_PAYMENT", "PAID");
        if (updated == 0) {
            log.warn("订单支付状态更新失败（并发变更）, orderId={}", orderId);
            return;
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PENDING_PAYMENT", "PAID"
        ));

        // ASYNC-01 断点 3：ORDER_PAID 与状态变更同事务发出（WMS 按独立 tag 订阅生成拣货单）——
        // 原 MQ 消费路径 markOrderPaid 缺少本事件，履约断链（T05 缺陷）
        outboxService.record(EventEnvelope.of("ORDER_PAID", 2,
                String.valueOf(orderId), 1, null,
                compensationJson(java.util.Map.of(
                        "orderId", orderId,
                        "userId", order.getUserId()))));

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
    @Transactional
    public OrderDTO shipOrder(Long orderId, String carrier, String trackingNo, Long warehouseId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        if (!"PAID".equals(order.getStatus())) {
            throw new BusinessException("ORDER_STATUS_ERROR", "当前订单状态不允许发货");
        }
        // WMS-01 闭环：无运单不得发货——先在 WMS 建立真实包裹并出库
        if (carrier == null || carrier.isBlank() || trackingNo == null || trackingNo.isBlank()) {
            throw new BusinessException("TRACKING_NO_REQUIRED", "承运商与运单号不能为空");
        }
        com.cloudmart.common.api.ApiResponse<Map<String, Object>> packageResp =
                wmsShippingFeignClient.createShipping(new java.util.LinkedHashMap<>(java.util.Map.of(
                        "orderId", orderId,
                        "carrier", carrier,
                        "trackingNo", trackingNo,
                        "receiverName", order.getReceiverName() == null ? "" : order.getReceiverName(),
                        "receiverPhone", order.getReceiverPhone() == null ? "" : order.getReceiverPhone(),
                        "receiverAddress", order.getReceiverAddress() == null ? "" : order.getReceiverAddress())));
        if (packageResp == null || !packageResp.success() || packageResp.data() == null
                || packageResp.data().get("id") == null) {
            throw new BusinessException("WMS_CREATE_FAILED", "包裹创建失败，发货未完成");
        }
        Long shipmentId = ((Number) packageResp.data().get("id")).longValue();
        wmsShippingFeignClient.updateStatus(shipmentId, "SHIPPED");

        // CAS 推进订单（与 ORDER_SHIPPED 事件消费者幂等，先到先赢）
        int updated = orderMapper.updateStatusAndShippedAtIfMatch(orderId, "PAID", "SHIPPED");
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "PAID", "SHIPPED"
        ));

        redisTemplate.delete(ORDER_TIMEOUT_KEY_PREFIX + orderId);

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

        // T02：审批经内部退款接口提交渠道退款单（refundNo 稳定 = RF+orderId，审批重复不重复退钱）。
        // 审批只提交，渠道确认才 SUCCEEDED——返回状态是渠道事实，不是审批结果：
        //   SUCCEEDED（MOCK 同构同步确认）→ 本事务推进 REFUNDED + 释放库存 + 退券；
        //   PROCESSING/UNKNOWN → 订单停留 REFUNDING，由 REFUND_SUCCEEDED 事件驱动推进；
        //   支付服务不可用/渠道未接入 → 明确失败，不改订单状态（QA06）。
        String refundNo = "RF" + orderId;
        Map<String, Object> refundRequest = new java.util.HashMap<>();
        refundRequest.put("refundNo", refundNo);
        refundRequest.put("orderId", orderId);
        refundRequest.put("amount", order.getPayAmount());
        refundRequest.put("currency", "CNY");
        refundRequest.put("reasonCode", "ORDER_REFUND");
        ApiResponse<Map<String, Object>> refundResp = refundFeignClient.createRefund(refundRequest);
        if (refundResp == null || !refundResp.success() || refundResp.data() == null) {
            throw new BusinessException("REFUND_SUBMIT_FAILED", "退款提交失败，请稍后重试");
        }
        String refundStatus = String.valueOf(refundResp.data().get("status"));
        if (!"SUCCEEDED".equals(refundStatus)) {
            // PROCESSING/UNKNOWN：订单停留 REFUNDING，事件驱动收敛
            log.info("[T02] 退款处理中, orderId={}, refundNo={}, channelStatus={}", orderId, refundNo, refundStatus);
            return buildOrderDto(orderId);
        }

        int updated = orderMapper.updateStatusToRefunded(orderId, "REFUNDING", "REFUNDED");
        if (updated == 0) {
            // 已被事件驱动路径推进：审批重放幂等收敛（QA07）
            log.info("订单已推进为 REFUNDED（事件先行），审批幂等返回, orderId={}", orderId);
            return buildOrderDto(orderId);
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "REFUNDING", "REFUNDED"
        ));

        releaseStockForOrder(orderId);

        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), orderId);
        }

        return buildOrderDto(orderId);
    }

    /** 组装订单详情 DTO（退款流程复用） */
    private OrderDTO buildOrderDto(Long orderId) {
        Order current = orderMapper.selectById(orderId);
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
        return orderConverter.toDTO(current, orderConverter.toItemDTOList(items));
    }

    /**
     * T02：REFUND_SUCCEEDED 事件驱动的退款推进（Inbox 幂等消费）。
     * CAS REFUNDING → REFUNDED：事件与审批同步推进竞争时只胜出一次（QA07）。
     */
    @Override
    @Transactional
    public void notifyRefundSucceeded(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        int updated = orderMapper.updateStatusToRefunded(orderId, "REFUNDING", "REFUNDED");
        if (updated == 0) {
            log.info("退款成功事件幂等跳过（订单非 REFUNDING）, orderId={}, status={}", orderId, order.getStatus());
            return;
        }
        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "REFUNDING", "REFUNDED"
        ));
        releaseStockForOrder(orderId);
        if (order.getCouponId() != null) {
            returnCouponForOrder(order.getCouponId(), orderId);
        }
        log.info("[T02] 退款事件驱动订单推进 REFUNDED, orderId={}", orderId);
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

        // T02/QA08：恢复退款前履约状态（before_refund_status），不再一律回 PAID；
        // 0 行 = 状态已变更或缺退款前状态（历史数据需人工核查）
        String restoredStatus = order.getBeforeRefundStatus() == null ? "PAID" : order.getBeforeRefundStatus();
        int updated = orderMapper.updateStatusRejectRefund(orderId, "REFUNDING", rejectReason);
        if (updated == 0) {
            throw new BusinessException("ORDER_STATUS_ERROR", "订单状态已变更，请刷新重试");
        }

        publishOutboxEvent(new OrderStatusChangeMessage(
                orderId, order.getUserId(), "REFUNDING", restoredStatus
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

    /**
     * RISK-01：下单前置风控——REJECT 抛 RISK_REJECTED；REVIEW 放行但留审计记录
     * （人工复核在风控后台）；风控服务不可用由降级工厂 fail-closed 拒绝。
     */
    private void performRiskCheck(Long userId, String actionType) {
        var response = riskFeignClient.check(java.util.Map.of(
                "userId", userId, "actionType", actionType));
        if (response == null || !response.success() || response.data() == null) {
            throw new BusinessException("RISK_SERVICE_UNAVAILABLE", "风控服务暂不可用，下单被拒绝");
        }
        String result = String.valueOf(response.data().getOrDefault("result", "PASS"));
        if ("REJECT".equals(result)) {
            String reason = String.valueOf(response.data().getOrDefault("reason", "触发风控规则"));
            log.warn("[RISK01] 下单被风控拒绝 userId={} reason={}", userId, reason);
            throw new BusinessException("RISK_REJECTED", "下单被拒绝：" + reason);
        }
    }

    /**
     * TRADE-01：按 skuId 从商品服务取权威价格/名称/图片/属性，覆盖客户端声明值。
     * SKU 缺失或已下架直接拒绝下单；商品服务不可用 fail-closed。
     */
    private List<CreateOrderRequest.OrderItemInput> overrideItemsFromProduct(
            List<CreateOrderRequest.OrderItemInput> items) {
        List<Long> skuIds = items.stream().map(CreateOrderRequest.OrderItemInput::skuId)
                .distinct().toList();
        ApiResponse<List<Map<String, Object>>> response = productFeignClient.getSkusBatch(skuIds);
        if (response == null || !response.success() || response.data() == null) {
            throw new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品服务暂不可用，无法下单");
        }
        Map<Long, Map<String, Object>> skuMap = new java.util.LinkedHashMap<>();
        for (Map<String, Object> sku : response.data()) {
            if (sku.get("skuId") instanceof Number n) {
                skuMap.put(n.longValue(), sku);
            }
        }
        // 构造新列表——入参可能是不可变 List（如 List.of）
        List<CreateOrderRequest.OrderItemInput> overridden = new ArrayList<>(items.size());
        for (CreateOrderRequest.OrderItemInput item : items) {
            Map<String, Object> sku = skuMap.get(item.skuId());
            if (sku == null) {
                throw new BusinessException("SKU_NOT_FOUND", "商品不存在: SKU " + item.skuId());
            }
            if (!Integer.valueOf(1).equals(sku.get("status"))) {
                throw new BusinessException("SKU_OFF_SALE", "商品已下架: SKU " + item.skuId());
            }
            BigDecimal authoritativePrice = toBigDecimal(sku.get("price"), item.skuId());
            overridden.add(new CreateOrderRequest.OrderItemInput(
                    toLongSafely(sku.get("productId")),
                    item.skuId(),
                    item.quantity(),
                    (String) sku.get("productName"),
                    (String) sku.get("image"),
                    (String) sku.get("attributes"),
                    authoritativePrice));
        }
        return overridden;
    }

    private BigDecimal toBigDecimal(Object value, Long skuId) {
        if (value instanceof BigDecimal amount) {
            return amount;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        throw new BusinessException("SKU_PRICE_INVALID", "商品价格数据异常: SKU " + skuId);
    }

    private Long toLongSafely(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /** T04：预占失败补偿——按真实 orderId 释放，台账驱动 CAS，不会影响其他订单的预占 */
    private void compensateDeductedStock(List<CreateOrderRequest.OrderItemInput> deductedItems, Long orderId) {
        for (CreateOrderRequest.OrderItemInput item : deductedItems) {
            try {
                InventoryReleaseRequest releaseReq = new InventoryReleaseRequest(
                        item.skuId(), item.quantity(), orderId);
                inventoryFeignClient.releaseStock(releaseReq);
            } catch (Exception ex) {
                // ASYNC-01：失败登记持久化补偿任务（orderId+skuId 幂等，重复登记不叠加）
                log.error("补偿释放库存失败, 已登记补偿任务, orderId={}, skuId={}: {}",
                        orderId, item.skuId(), ex.getMessage());
                compensationTaskService.createIfAbsent(
                        "stock-release:" + orderId + ":" + item.skuId(), "stock-release",
                        String.valueOf(orderId), compensationPayload(orderId, item.skuId(), item.quantity()));
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
        // COUPON-01：优惠封顶——任何券的优惠不得超过商品总额（防 payAmount 为负）；
        // 折扣率必须在 (0,1] 区间（如 0.90 = 9 折），配置异常按无优惠处理并告警
        BigDecimal discount = BigDecimal.ZERO;
        if ("AMOUNT_OFF".equals(coupon.templateType()) && coupon.discountAmount() != null) {
            discount = coupon.discountAmount();
        } else if ("PERCENT_OFF".equals(coupon.templateType()) && coupon.discountRate() != null) {
            BigDecimal rate = coupon.discountRate();
            if (rate.compareTo(BigDecimal.ZERO) <= 0 || rate.compareTo(BigDecimal.ONE) > 0) {
                log.warn("[COUPON01] 折扣率配置越界，按无优惠处理, couponId={}, rate={}",
                        coupon.id(), rate);
                return BigDecimal.ZERO;
            }
            discount = totalAmount.subtract(totalAmount.multiply(rate));
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        return discount.min(totalAmount);
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
        throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "下单服务繁忙，请稍后重试");
    }

    /**
     * Sentinel 降级只接管流量类异常；业务/数据异常必须原样上抛。此前一律返回 null，
     * 调用方 createOrderFromQuote 对 null 取 id() 直接 NPE（INTERNAL_ERROR 500），
     * 且真实失败原因被吞掉。
     */
    public OrderDTO createOrderFallback(Long userId, CreateOrderRequest request, Throwable throwable) {
        log.warn("createOrder fallback triggered: {}", throwable.getMessage());
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "下单服务暂时不可用，请稍后重试");
    }

    public OrderDTO cancelOrderBlockHandler(Long userId, Long orderId, BlockException ex) {
        log.warn("cancelOrder blocked by Sentinel: {}", ex.getRule());
        throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "下单服务繁忙，请稍后重试");
    }

    @Override
    @Transactional
    public OrderDTO createOrderFromQuote(Long userId, Long quoteId, String receiverName,
                                         String receiverPhone, String receiverAddress) {
        // TRADE-01：归属与状态前置校验（他人/不存在一律 404）
        com.cloudmart.order.entity.OrderQuote quote = orderQuoteMapper.selectById(quoteId);
        if (quote == null || !quote.getUserId().equals(userId)) {
            throw new BusinessException("QUOTE_NOT_FOUND", "报价不存在");
        }

        // CAS 消费报价（与建单同事务：建单失败回滚后报价自动恢复 ACTIVE）；
        // 并发重复下单同一报价只有一个赢家
        if (orderQuoteMapper.consume(quoteId, 0L) == 0) {
            throw new BusinessException("QUOTE_NOT_AVAILABLE", "报价已使用或已过期，请重新报价");
        }

        // 服务端构造下单请求：金额/商品名/图片/属性全部取报价快照，
        // 客户端在 v2 接口上没有可提交的价格字段
        List<com.cloudmart.order.entity.OrderQuoteItem> quoteItems = orderQuoteItemMapper.selectList(
                new LambdaQueryWrapper<com.cloudmart.order.entity.OrderQuoteItem>()
                        .eq(com.cloudmart.order.entity.OrderQuoteItem::getQuoteId, quoteId));
        List<CreateOrderRequest.OrderItemInput> items = quoteItems.stream()
                .map(qi -> new CreateOrderRequest.OrderItemInput(
                        qi.getProductId(), qi.getSkuId(), qi.getQuantity(),
                        qi.getProductName(), qi.getSkuImage(), qi.getSkuAttributes(), qi.getPrice()))
                .toList();

        // requestId 绑定报价：同报价重试收敛为同一幂等意图
        String requestId = "quote-" + quoteId;
        CreateOrderRequest request = new CreateOrderRequest(
                requestId, items, receiverName, receiverPhone, receiverAddress,
                quote.getCouponId(), null);

        OrderDTO order = selfProvider.getObject().createOrder(userId, request);
        // 双保险：createOrder 在任何降级路径都不应返回 null；万一返回则明确失败而非 NPE
        if (order == null) {
            throw new BusinessException("ORDER_CREATE_FAILED", "订单创建失败，请稍后重试");
        }

        orderQuoteMapper.updateConsumedBy(quoteId, order.id());
        return order;
    }

    @Override
    public boolean hasOpenOrders(Long userId) {
        // USER-01/W03：未结 = 资金/履约/售后任一未完成——REFUNDING 退款处理中必须阻塞注销
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .in(Order::getStatus, "PENDING_PAYMENT", "PAID", "SHIPPED", "REFUNDING"));
        return count != null && count > 0;
    }

    @Override
    public java.util.Map.Entry<java.util.List<com.cloudmart.order.dto.OrderInternalInfoDTO>, Long>
            listPaidOrdersForReconciliation(int page, int size) {
        // OPS-01：对账核对——已推进资金状态的订单分页（PAID/SHIPPED/COMPLETED）
        Page<Order> result = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Order>()
                        .in(Order::getStatus, "PAID", "SHIPPED", "COMPLETED")
                        .orderByAsc(Order::getId));
        var records = result.getRecords().stream()
                .map(o -> new com.cloudmart.order.dto.OrderInternalInfoDTO(
                        o.getId(), o.getUserId(), o.getStatus(), o.getPayAmount()))
                .toList();
        return java.util.Map.entry(records, result.getTotal());
    }

    @Override
    public int autoConfirmReceipts(int days) {
        // WMS-01 余量：自动收货——CAS SHIPPED→COMPLETED 逐单推进（与用户手动确认
        // 及退款并发安全，先到先赢），每单发布状态事件；批量上限防长事务
        if (days <= 0) {
            throw new BusinessException("INVALID_ARGUMENT", "自动收货天数必须大于 0");
        }
        List<Long> ids = orderMapper.findAutoConfirmableOrderIds(days, 500);
        int confirmed = 0;
        for (Long orderId : ids) {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                continue;
            }
            int updated = orderMapper.updateStatusAndCompletedAtIfMatch(orderId, "SHIPPED", "COMPLETED");
            if (updated > 0) {
                publishOutboxEvent(new OrderStatusChangeMessage(
                        orderId, order.getUserId(), "SHIPPED", "COMPLETED"));
                confirmed++;
            }
        }
        if (confirmed > 0) {
            log.info("[WMS01] 自动收货完成，本轮确认 {} 单（天数阈值 {} 天）", confirmed, days);
        }
        return confirmed;
    }

    @Override
    public List<Long> findCompletedOrderIdsWithSku(Long userId, Long skuId) {
        return orderMapper.findCompletedOrderIdsWithSku(userId, skuId);
    }

    @Override
    public com.cloudmart.order.dto.OrderInternalInfoDTO getInternalOrderInfo(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "订单不存在");
        }
        return new com.cloudmart.order.dto.OrderInternalInfoDTO(order.getId(), order.getUserId(), order.getStatus(), order.getPayAmount());
    }
}
