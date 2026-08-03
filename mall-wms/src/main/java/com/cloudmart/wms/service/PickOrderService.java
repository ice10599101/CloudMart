package com.cloudmart.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.dto.PickOrderDTO;

public interface PickOrderService {

    PickOrderDTO createPickOrder(CreatePickOrderRequest request);

    PickOrderDTO startPick(Long pickOrderId, Long assignedUserId);

    PickOrderDTO confirmPicked(Long pickOrderId);

    PickOrderDTO confirmPacked(Long pickOrderId);

    PickOrderDTO getPickOrder(Long pickOrderId);

    IPage<PickOrderDTO> listPickOrders(String status, Long warehouseId, int page, int size);

    PickOrderDTO findByOrderId(Long orderId);
}
