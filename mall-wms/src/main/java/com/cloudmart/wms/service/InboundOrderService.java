package com.cloudmart.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;

public interface InboundOrderService {

    InboundOrderDTO createInboundOrder(CreateInboundOrderRequest request);

    InboundOrderDTO receiveItem(Long inboundOrderId, Long itemId, Integer receivedQuantity);

    InboundOrderDTO completeInbound(Long inboundOrderId);

    InboundOrderDTO getInboundOrder(Long inboundOrderId);

    IPage<InboundOrderDTO> listInboundOrders(String status, Long warehouseId, int page, int size);
}
