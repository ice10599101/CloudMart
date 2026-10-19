package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.dto.OrderInternalInfoDTO;
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
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ShippingServiceImpl implements ShippingService {

    private final ShippingOrderMapper shippingOrderMapper;
    private final ShippingTrackingMapper shippingTrackingMapper;
    private final WmsConverter wmsConverter;
    private final OrderInfoFeignClient orderInfoFeignClient;
    private final com.cloudmart.common.async.outbox.OutboxService outboxService;
    private final com.cloudmart.wms.repository.WarehouseMapper warehouseMapper;
    private final ObjectMapper objectMapper;

    private static final java.util.Set<String> ALLOWED_STATUSES =
            java.util.Set.of("PENDING", "PICKING", "SHIPPED", "DELIVERED");

    /** T05：一单一包裹 → 出库事件的稳定事实 ID（重复投递由 Outbox 唯一键与 Inbox 去重）。 */
    private static final String ORDER_SHIPPED_EVENT_PREFIX = "ORDER_SHIPPED:";
    /**
     * 状态机允许的迁移（T05）：PENDING→PICKING→SHIPPED→DELIVERED。
     * 允许 PENDING→SHIPPED 的快速出库，但必须满足运单与订单可出库条件；
     * 删除 PENDING/PICKING 直接 DELIVERED 的越级迁移（跳过出库事实会导致订单永不推进）。
     */
    private static final java.util.Map<String, java.util.Set<String>> ALLOWED_TRANSITIONS = java.util.Map.of(
            "PENDING", java.util.Set.of("PICKING", "SHIPPED"),
            "PICKING", java.util.Set.of("SHIPPED"),
            "SHIPPED", java.util.Set.of("DELIVERED"),
            "DELIVERED", java.util.Set.of());

    public ShippingServiceImpl(ShippingOrderMapper shippingOrderMapper,
                               ShippingTrackingMapper shippingTrackingMapper,
                               WmsConverter wmsConverter, OrderInfoFeignClient orderInfoFeignClient,
                               com.cloudmart.common.async.outbox.OutboxService outboxService,
                               com.cloudmart.wms.repository.WarehouseMapper warehouseMapper,
                               ObjectMapper objectMapper) {
        this.shippingOrderMapper = shippingOrderMapper;
        this.shippingTrackingMapper = shippingTrackingMapper;
        this.wmsConverter = wmsConverter;
        this.orderInfoFeignClient = orderInfoFeignClient;
        this.outboxService = outboxService;
        this.warehouseMapper = warehouseMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    @SentinelResource(value = "createShippingOrder", fallback = "createShippingOrderFallback")
    public ShippingOrderVO createShipping(CreateShippingRequest request) {
        // WMS-01：无运单不得建档（真实单号必填）
        if (request.trackingNo() == null || request.trackingNo().isBlank()) {
            throw new BusinessException("TRACKING_NO_REQUIRED", "承运商运单号不能为空");
        }
        // T21：核对权威订单（可出库状态 + 权威收件快照），fail-closed——未付款/已取消不可建。
        OrderInternalInfoDTO orderSnap = requireOrderShippable(request.orderId());
        // T21：仓库必须存在且启用——请求指定则校验，未指定则取第一个启用仓（不无序取第一行）
        Long warehouseId = resolveWarehouse(request.warehouseId());

        ShippingOrder order = new ShippingOrder();
        order.setOrderId(request.orderId());
        order.setWarehouseId(warehouseId);
        // T21：内部 shippingNo 用统一雪花 ID，不再用 "SF+当前毫秒" 冒充承运单号；
        // 真实运单号单独存 trackingNo。
        order.setShippingNo(String.valueOf(IdWorker.getId()));
        order.setCarrier(request.carrier());
        order.setTrackingNo(request.trackingNo().trim());
        order.setStatus("PENDING");
        // T21：收件信息以订单快照为准（订单服务未返回时回退请求兜底），不信任请求篡改
        order.setReceiverName(orderSnap.receiverName() != null ? orderSnap.receiverName() : request.receiverName());
        order.setReceiverPhone(orderSnap.receiverPhone() != null ? orderSnap.receiverPhone() : request.receiverPhone());
        order.setReceiverAddress(orderSnap.receiverAddress() != null ? orderSnap.receiverAddress() : request.receiverAddress());
        try {
            shippingOrderMapper.insert(order);
        } catch (DuplicateKeyException duplicate) {
            // T21：order_id 唯一约束下并发重复建包回读——同单同运单号幂等返回，不同单号/承运商报冲突
            ShippingOrder existing = selectByOrderId(request.orderId());
            if (existing == null) {
                throw duplicate;
            }
            if (!request.trackingNo().trim().equals(existing.getTrackingNo())
                    || !java.util.Objects.equals(request.carrier(), existing.getCarrier())) {
                throw new BusinessException("SHIPPING_CONFLICT",
                        "该订单已存在包裹且运单号/承运商不一致，请核对后重试");
            }
            log.info("[WMS01] 并发重复建包回读已有包裹 orderId={}", request.orderId());
            return toVO(existing);
        }
        return toVO(order);
    }

    /** T21：仓库分配——请求指定则校验存在且启用；未指定则取第一个启用仓（按 id 升序）。 */
    private Long resolveWarehouse(Long warehouseId) {
        if (warehouseId != null) {
            com.cloudmart.wms.entity.Warehouse warehouse = warehouseMapper.selectById(warehouseId);
            if (warehouse == null || !Integer.valueOf(1).equals(warehouse.getStatus())) {
                throw new BusinessException("WAREHOUSE_UNAVAILABLE",
                        "仓库不存在或已停用: " + warehouseId);
            }
            return warehouseId;
        }
        com.cloudmart.wms.entity.Warehouse first = warehouseMapper.selectOne(
                new LambdaQueryWrapper<com.cloudmart.wms.entity.Warehouse>()
                        .eq(com.cloudmart.wms.entity.Warehouse::getStatus, 1)
                        .orderByAsc(com.cloudmart.wms.entity.Warehouse::getId)
                        .last("LIMIT 1"));
        if (first == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "无可用仓库，无法创建物流订单");
        }
        return first.getId();
    }

    private ShippingOrder selectByOrderId(Long orderId) {
        return shippingOrderMapper.selectOne(new LambdaQueryWrapper<ShippingOrder>()
                .eq(ShippingOrder::getOrderId, orderId)
                .last("LIMIT 1"));
    }

    @Override
    public ShippingOrderVO getByOrderId(Long orderId, Long callerUserId) {
        // SEC-04：用户调用先核订单权威归属（fail-closed：订单服务不可用即拒绝）
        requireOrderVisible(orderId, callerUserId);
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
    public List<ShippingTrackingDTO> listTrackingsByOrderId(Long orderId, Long callerUserId) {
        requireOrderVisible(orderId, callerUserId);
        ShippingOrder order = shippingOrderMapper.selectOne(
                new LambdaQueryWrapper<ShippingOrder>().eq(ShippingOrder::getOrderId, orderId));
        if (order == null) {
            // 尚未创建包裹：订单详情显示“待出库”，不是错误
            return List.of();
        }
        return shippingTrackingMapper.selectList(
                        new LambdaQueryWrapper<ShippingTracking>()
                                .eq(ShippingTracking::getShippingOrderId, order.getId())
                                .orderByAsc(ShippingTracking::getHappenedAt))
                .stream().map(this::toTrackingDTO).toList();
    }

    /** SEC-04：用户调用必须与订单归属一致；管理员/服务调用方跳过。 */
    private void requireOrderVisible(Long orderId, Long callerUserId) {
        if (callerUserId == null) {
            return;
        }
        com.cloudmart.common.api.ApiResponse<com.cloudmart.wms.dto.OrderInternalInfoDTO> orderInfo =
                orderInfoFeignClient.getOrderInfo(orderId);
        if (orderInfo == null || !orderInfo.success() || orderInfo.data() == null) {
            throw new BusinessException("SHIPPING_ORDER_NOT_FOUND", "物流订单不存在");
        }
        if (!callerUserId.equals(orderInfo.data().userId())) {
            throw new BusinessException("SHIPPING_FORBIDDEN", "无权查看该订单的物流信息");
        }
    }

    @Override
    @Transactional
    @SentinelResource(value = "updateShippingStatus", fallback = "updateStatusFallback")
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
        // T05：重复相同终态按业务键返回原结果，不报误导性失败（重放/双击幂等）
        if (status.equals(current)) {
            log.info("[WMS01] 重复状态推进按幂等返回 shippingOrderId={} status={}", shippingOrderId, status);
            return toVO(order);
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
        // T05：出库前核对权威订单状态（订单取消/退款并发下不得继续出库）。
        // fail-closed：订单服务不可用即拒绝出库，不以本地状态猜测。
        if ("SHIPPED".equals(status)) {
            requireOrderShippable(order.getOrderId());
        }

        int updated = "SHIPPED".equals(status)
                ? shippingOrderMapper.updateStatusIfMatch(shippingOrderId, current, status)
                : shippingOrderMapper.updateStatusPlainIfMatch(shippingOrderId, current, status);
        if (updated == 0) {
            // 并发败者：若对手已推进到同一目标状态，仍按幂等返回原结果
            ShippingOrder latest = shippingOrderMapper.selectById(shippingOrderId);
            if (latest != null && status.equals(latest.getStatus())) {
                return toVO(latest);
            }
            throw new BusinessException("SHIPPING_STATUS_CONFLICT", "物流状态已变更，请刷新重试");
        }

        // T05：出库事实与 Outbox 同一事务——record 抛错则状态更新一并回滚（方法不再吞异常）。
        // eventId 用稳定的出库事实 ID（一单一包裹），消息重复投递由 Outbox 唯一键与 Inbox 去重。
        if ("SHIPPED".equals(status)) {
            ShippingOrder shipped = shippingOrderMapper.selectById(shippingOrderId);
            OutboxOrderShippedPayload payload = new OutboxOrderShippedPayload(
                    order.getOrderId(), shippingOrderId, order.getCarrier(),
                    order.getTrackingNo(), shipped == null ? null : shipped.getShippedAt());
            outboxService.record(new com.cloudmart.common.async.EventEnvelope(
                    ORDER_SHIPPED_EVENT_PREFIX + order.getOrderId(),
                    "ORDER_SHIPPED", 2, String.valueOf(order.getOrderId()), 1,
                    System.currentTimeMillis(), MDC.get("requestId"), writePayload(payload)));
        }

        return toVO(shippingOrderMapper.selectById(shippingOrderId));
    }

    /** T05/T21：出库前订单必须处于可发货状态（PAID）；订单服务不可用时 fail-closed。
     *  返回订单内部信息（状态 + 权威收件快照），供建包复用，避免重复请求。 */
    private OrderInternalInfoDTO requireOrderShippable(Long orderId) {
        com.cloudmart.common.api.ApiResponse<OrderInternalInfoDTO> orderInfo =
                orderInfoFeignClient.getOrderInfo(orderId);
        if (orderInfo == null || !orderInfo.success() || orderInfo.data() == null) {
            throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "订单服务暂不可用，无法确认可出库状态");
        }
        if (!"PAID".equals(orderInfo.data().status())) {
            throw new BusinessException("SHIPPING_ORDER_NOT_SHIPPABLE",
                    "订单当前状态不允许出库: " + orderInfo.data().status());
        }
        return orderInfo.data();
    }

    private ShippingOrderVO toVO(ShippingOrder order) {
        List<ShippingTracking> trackings = shippingTrackingMapper.selectList(
                new LambdaQueryWrapper<ShippingTracking>()
                        .eq(ShippingTracking::getShippingOrderId, order.getId()));
        return wmsConverter.fromShippingOrderDTO(
                toOrderDTO(order, trackings.stream().map(this::toTrackingDTO).toList()));
    }

    private String writePayload(OutboxOrderShippedPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            // 序列化失败必须上抛：吞掉会留下“已出库但订单永不知道”的不一致
            throw new BusinessException("SHIPPED_EVENT_SERIALIZE_ERROR", "出库事件序列化失败");
        }
    }

    /** T05：ORDER_SHIPPED 事件载荷（类型化，替代手工拼 JSON）。 */
    public record OutboxOrderShippedPayload(Long orderId, Long shipmentId, String carrier,
                                            String trackingNo, LocalDateTime shippedAt) {
    }

    public ShippingOrderVO updateStatusFallback(Long shippingOrderId, String status, Throwable throwable) {
        log.warn("updateShippingStatus fallback triggered, shippingOrderId={}: {}",
                shippingOrderId, throwable.getMessage());
        throw new BusinessException("WMS_SERVICE_UNAVAILABLE", "物流服务暂时不可用，请稍后重试");
    }

    @Override
    public IPage<ShippingOrderVO> listShipping(String status, Long warehouseId, Long orderId, int page, int size) {
        LambdaQueryWrapper<ShippingOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(ShippingOrder::getStatus, status);
        }
        if (warehouseId != null) {
            wrapper.eq(ShippingOrder::getWarehouseId, warehouseId);
        }
        // TRADE-03：订单维度筛选全链透传（管理页按订单查物流）
        if (orderId != null) {
            wrapper.eq(ShippingOrder::getOrderId, orderId);
        }
        // T26：pageSize 收口（正数 + 上限 100）与稳定排序（时间+ID），避免深翻页与同秒乱序
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        wrapper.orderByDesc(ShippingOrder::getCreatedAt).orderByDesc(ShippingOrder::getId);
        Page<ShippingOrder> orderPage = shippingOrderMapper.selectPage(new Page<>(safePage, safeSize), wrapper);
        // T26：N+1 修复——按本页运单 ID 一次批量取轨迹，按运单分组（100 条列表 SQL 次数不随条数线性增长）
        List<Long> pageIds = orderPage.getRecords().stream().map(ShippingOrder::getId).toList();
        Map<Long, List<ShippingTracking>> trackingsByOrderId = pageIds.isEmpty() ? Map.of()
                : shippingTrackingMapper.selectList(
                        new LambdaQueryWrapper<ShippingTracking>()
                                .in(ShippingTracking::getShippingOrderId, pageIds)
                                .orderByAsc(ShippingTracking::getHappenedAt))
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(ShippingTracking::getShippingOrderId));
        List<ShippingOrderVO> voList = orderPage.getRecords().stream()
                .map(order -> {
                    List<ShippingTracking> trackings =
                            trackingsByOrderId.getOrDefault(order.getId(), List.of());
                    ShippingOrderDTO dto = toOrderDTO(order, trackings.stream().map(this::toTrackingDTO).toList());
                    return wmsConverter.fromShippingOrderDTO(dto);
                })
                .toList();
        Page<ShippingOrderVO> resultPage = new Page<>(safePage, safeSize, orderPage.getTotal());
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
                entity.getTrackingNo(),
                entity.getCarrier(),
                entity.getStatus(),
                entity.getReceiverName(),
                entity.getReceiverPhone(),
                entity.getReceiverAddress(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getShippedAt(),
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
