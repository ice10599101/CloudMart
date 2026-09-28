package com.cloudmart.wms.service;

import com.cloudmart.wms.dto.CreateShippingRequest;
import com.cloudmart.wms.dto.ShippingTrackingDTO;
import com.cloudmart.wms.vo.ShippingOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.time.LocalDateTime;

public interface ShippingService {

    ShippingOrderVO createShipping(CreateShippingRequest request);

    /**
     * 按订单查询物流（SEC-04 对象归属）：用户调用校验订单归属，越权一律拒绝；
     * 管理员/服务调用方（callerUserId == null）跳过归属校验。
     */
    ShippingOrderVO getByOrderId(Long orderId, Long callerUserId);

    ShippingOrderVO updateStatus(Long id, String status);

    IPage<ShippingOrderVO> listShipping(String status, Long warehouseId, int page, int size);

    ShippingTrackingDTO addTracking(Long shippingOrderId, String location, String description, LocalDateTime happenedAt);
}
