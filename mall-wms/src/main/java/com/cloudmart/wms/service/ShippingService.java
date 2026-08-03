package com.cloudmart.wms.service;

import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.dto.ShippingTrackingDTO;
import com.cloudmart.wms.vo.ShippingOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.LocalDateTime;

public interface ShippingService {

    ShippingOrderVO createShipping(CreateShippingRequest request);

    ShippingOrderVO getByOrderId(Long orderId);

    ShippingOrderVO updateStatus(Long id, String status);

    IPage<ShippingOrderVO> listShipping(String status, Long warehouseId, int page, int size);

    ShippingTrackingDTO addTracking(Long shippingOrderId, String location, String description, LocalDateTime happenedAt);
}
