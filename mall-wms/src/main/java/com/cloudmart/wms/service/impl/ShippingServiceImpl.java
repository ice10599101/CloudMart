package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.dto.ShippingOrderDTO;
import com.cloudmart.wms.dto.ShippingTrackingDTO;
import com.cloudmart.wms.entity.ShippingOrder;
import com.cloudmart.wms.feign.OrderInfoFeignClient;
import com.cloudmart.wms.entity.ShippingTracking;
import com.cloudmart.wms.repository.ShippingOrderMapper;
import com.cloudmart.wms.repository.ShippingTrackingMapper;
import com.cloudmart.wms.service.ShippingService;
import com.cloudmart.wms.vo.ShippingOrderVO;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ShippingServiceImpl implements ShippingService {

    private final ShippingOrderMapper shippingOrderMapper;
    private final ShippingTrackingMapper shippingTrackingMapper;
    private final WmsConverter wmsConverter;
    private final OrderInfoFeignClient orderInfoFeignClient;
    private final com.cloudmart.common.async.outbox.OutboxService outboxService;

    private static final java.util.Set<String> ALLOWED_STATUSES =
            java.util.Set.of("PENDING", "PICKING", "SHIPPED", "DELIVERED");
    /** 状态机允许的迁移（WMS-01：拒绝任意字符串状态） */
    private static final java.util.Map<String, java.util.Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "PENDING", java.util.Set.of("PICKING", "SHIPPED", "DELIVERED"),
            "PICKING", java.util.Set.of("SHIPPED", "DELIVERED"),
            "SHIPPED", java.util.Set.of("DELIVERED"),
            "DELIVERED", java.util.Set.of());

    public ShippingServiceImpl(ShippingOrderMapper shippingOrderMapper,
                               ShippingTrackingMapper shippingTrackingMapper,
                               WmsConverter wmsConverter, OrderInfoFeignClient orderInfoFeignClient,
                               com.cloudmart.common.async.outbox.OutboxService outboxService) {
        this.shippingOrderMapper = shippingOrderMapper;
        this.shippingTrackingMapper = shippingTrackingMapper;
        this.wmsConverter = wmsConverter;
        this.orderInfoFeignClient = orderInfoFeignClient;
        this.outboxService = outboxService;
    }

    @Override
    @SentinelResource(value = "createShippingOrder", fallback = "createShippingOrderFallback")
    public ShippingOrderVO createShipping(CreateShippingRequest request) {
        // WMS-01：无运单不得建档（真实单号必填）；重复 paid 事件按订单幂等返回原包裹
        if (request.trackingNo() == null || request.trackingNo().isBlank()) {
            throw new BusinessException("TRACKING_NO_REQUIRED", "承运商运单号不能为空");
        }
        if (shippingOrderMapper.countByOrderId(request.orderId()) > 0) {
            log.info("[WMS01] 重复建包裹请求，返回已有包裹 orderId={}", request.orderId());
            return getByOrderId(request.orderId(), null);
        }
        ShippingOrder order = new ShippingOrder();
        order.setOrderId(request.orderId());
        order.setWarehouseId(request.warehouseId());
        order.setShippingNo("SF" + System.currentTimeMillis());
        order.setCarrier(request.carrier());
        order.setTrackingNo(request.trackingNo().trim());
        order.setStatus("PENDING");
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        shippingOrderMapper.insert(order);
        ShippingOrderDTO dto = toOrderDTO(order, Collections.emptyList());
        return wmsConverter.fromShippingOrderDTO(dto);
    }

    @Override
    public ShippingOrderVO getByOrderId(Long orderId, Long callerUserId) {
        // SEC-04：用户调用先核订单权威归属（fail-closed：订单服务不可用即拒绝）
        if (callerUserId != null) {
            com.cloudmart.common.api.ApiResponse<com.cloudmart.wms.dto.OrderInternalInfoDTO> orderInfo =
                    orderInfoFeignClient.getOrderInfo(orderId);
            if (orderInfo == null || !orderInfo.success() || orderInfo.data() == null) {
                throw new BusinessException("SHIPPING_ORDER_NOT_FOUND", "物流订单不存在");
            }
            if (!callerUserId.equals(orderInfo.data().userId())) {
                throw new BusinessException("SHIPPING_FORBIDDEN", "无权查看该订单的物流信息");
            }
        }
        ShippingOrder order = shippingOrderMapper.selectOne(
                new LambdaQueryWrapper<ShippingOrder>().eq(ShippingOrder::getOrderId, orderId)
        );
        if (order == null) {
            throw new BusinessException("SHIPPING_ORDER_NOT_FOUND", "物流订单不存在");
        }
        List<ShippingTracking> trackings = shippingTrackingMapper.selectList(
                new LambdaQueryWrapper<ShippingTracking>().eq(ShippingTracking::getShippingOrderId, order.getId())
        );
        ShippingOrderDTO dto = toOrderDTO(order, trackings.stream().map(this::toTrackingDTO).toList());
        return wmsConverter.fromShippingOrderDTO(dto);
    }

    @Override
    public ShippingOrderVO updateStatus(Long shippingOrderId, String status) {
        ShippingOrder order = shippingOrderMapper.selectById(shippingOrderId);
        if (order == null) {
            throw new BusinessException("SHIPPING_ORDER_NOT_FOUND", "物流订单不存在");
        }
        // WMS-01：状态机白名单 + 合法迁移——拒绝任意字符串状态
        String current = order.getStatus() == null ? "PENDING" : order.getStatus();
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new BusinessException("SHIPPING_STATUS_INVALID", "非法物流状态: " + status);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, java.util.Set.of()).contains(status)) {
            throw new BusinessException("SHIPPING_STATUS_TRANSITION_INVALID",
                    "不允许的状态迁移: " + current + " -> " + status);
        }
        // WMS-01：无运单不得 SHIPPED
        if ("SHIPPED".equals(status)
                && (order.getTrackingNo() == null || order.getTrackingNo().isBlank())) {
            throw new BusinessException("TRACKING_NO_REQUIRED", "缺少运单号，不允许出库");
        }

        int updated = "SHIPPED".equals(status)
                ? shippingOrderMapper.updateStatusIfMatch(shippingOrderId, current, status)
                : shippingOrderMapper.updateStatusPlainIfMatch(shippingOrderId, current, status);
        if (updated == 0) {
            throw new BusinessException("SHIPPING_STATUS_CONFLICT", "物流状态已变更，请刷新重试");
        }

        // WMS-01：出库即发布 ORDER_SHIPPED（Outbox 与状态更新同事务语义——本方法由
        // 调用方事务包裹；订单服务消费后推进订单 SHIPPED）
        if ("SHIPPED".equals(status)) {
            String payload = "{\"orderId\":" + order.getOrderId()
                    + ",\"shipmentId\":" + order.getId()
                    + ",\"carrier\":\"" + (order.getCarrier() == null ? "" : order.getCarrier())
                    + "\",\"trackingNo\":\"" + order.getTrackingNo() + "\"}";
            outboxService.record(com.cloudmart.common.async.EventEnvelope.of(
                    "ORDER_SHIPPED", 1, String.valueOf(order.getOrderId()), 1, null, payload));
        }

        ShippingOrder refreshed = shippingOrderMapper.selectById(shippingOrderId);
        List<ShippingTracking> trackings = shippingTrackingMapper.selectList(
                new LambdaQueryWrapper<ShippingTracking>().eq(ShippingTracking::getShippingOrderId, shippingOrderId)
        );
        ShippingOrderDTO dto = toOrderDTO(refreshed, trackings.stream().map(this::toTrackingDTO).toList());
        return wmsConverter.fromShippingOrderDTO(dto);
    }

    @Override
    public IPage<ShippingOrderVO> listShipping(String status, Long warehouseId, int page, int size) {
        LambdaQueryWrapper<ShippingOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ShippingOrder::getStatus, status);
        }
        if (warehouseId != null) {
            wrapper.eq(ShippingOrder::getWarehouseId, warehouseId);
        }
        wrapper.orderByDesc(ShippingOrder::getCreatedAt);
        Page<ShippingOrder> orderPage = shippingOrderMapper.selectPage(new Page<>(page, size), wrapper);
        List<ShippingOrderVO> voList = orderPage.getRecords().stream()
                .map(order -> {
                    List<ShippingTracking> trackings = shippingTrackingMapper.selectList(
                            new LambdaQueryWrapper<ShippingTracking>().eq(ShippingTracking::getShippingOrderId, order.getId())
                    );
                    ShippingOrderDTO dto = toOrderDTO(order, trackings.stream().map(this::toTrackingDTO).toList());
                    return wmsConverter.fromShippingOrderDTO(dto);
                })
                .toList();
        Page<ShippingOrderVO> resultPage = new Page<>(page, size, orderPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public ShippingTrackingDTO addTracking(Long shippingOrderId, String location, String description, LocalDateTime happenedAt) {
        ShippingOrder order = shippingOrderMapper.selectById(shippingOrderId);
        if (order == null) {
            throw new BusinessException("SHIPPING_ORDER_NOT_FOUND", "物流订单不存在");
        }
        ShippingTracking tracking = new ShippingTracking();
        tracking.setShippingOrderId(shippingOrderId);
        tracking.setLocation(location);
        tracking.setDescription(description);
        tracking.setHappenedAt(happenedAt);
        shippingTrackingMapper.insert(tracking);
        return toTrackingDTO(tracking);
    }

    private ShippingOrderDTO toOrderDTO(ShippingOrder entity, List<ShippingTrackingDTO> trackings) {
        return new ShippingOrderDTO(
                entity.getId(),
                entity.getOrderId(),
                entity.getWarehouseId(),
                entity.getShippingNo(),
                entity.getCarrier(),
                entity.getStatus(),
                entity.getReceiverName(),
                entity.getReceiverPhone(),
                entity.getReceiverAddress(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                trackings
        );
    }

    private ShippingTrackingDTO toTrackingDTO(ShippingTracking entity) {
        return new ShippingTrackingDTO(
                entity.getId(),
                entity.getShippingOrderId(),
                entity.getLocation(),
                entity.getDescription(),
                entity.getHappenedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public ShippingOrderVO createShippingOrderFallback(CreateShippingRequest request, Throwable throwable) {
        log.warn("createShippingOrder fallback triggered, orderId={}: {}", request.orderId(), throwable.getMessage());
        throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "物流服务暂时不可用，请稍后重试");
    }
}
