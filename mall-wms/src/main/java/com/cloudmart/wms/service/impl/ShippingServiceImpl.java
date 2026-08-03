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

    public ShippingServiceImpl(ShippingOrderMapper shippingOrderMapper,
                               ShippingTrackingMapper shippingTrackingMapper,
                               WmsConverter wmsConverter) {
        this.shippingOrderMapper = shippingOrderMapper;
        this.shippingTrackingMapper = shippingTrackingMapper;
        this.wmsConverter = wmsConverter;
    }

    @Override
    @SentinelResource(value = "createShippingOrder", fallback = "createShippingOrderFallback")
    public ShippingOrderVO createShipping(CreateShippingRequest request) {
        ShippingOrder order = new ShippingOrder();
        order.setOrderId(request.orderId());
        order.setWarehouseId(request.warehouseId());
        order.setShippingNo("SF" + System.currentTimeMillis());
        order.setCarrier(request.carrier());
        order.setStatus("PENDING");
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        shippingOrderMapper.insert(order);
        ShippingOrderDTO dto = toOrderDTO(order, Collections.emptyList());
        return wmsConverter.fromShippingOrderDTO(dto);
    }

    @Override
    public ShippingOrderVO getByOrderId(Long orderId) {
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
        order.setStatus(status);
        shippingOrderMapper.updateById(order);
        List<ShippingTracking> trackings = shippingTrackingMapper.selectList(
                new LambdaQueryWrapper<ShippingTracking>().eq(ShippingTracking::getShippingOrderId, order.getId())
        );
        ShippingOrderDTO dto = toOrderDTO(order, trackings.stream().map(this::toTrackingDTO).toList());
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
