package com.cloudmart.wms.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;

public interface InboundOrderService {

    InboundOrderDTO createInboundOrder(CreateInboundOrderRequest request);

    InboundOrderDTO receiveItem(Long inboundOrderId, Long itemId, Integer receivedQuantity);

    InboundOrderDTO completeInbound(Long inboundOrderId);

    InboundOrderDTO getInboundOrder(Long inboundOrderId);

    /** T11 切片三：按类型+关联单号查询（退货入库幂等判定用），不存在返回 null */
    InboundOrderDTO findByTypeAndReferenceNo(String type, String referenceNo);

    IPage<InboundOrderDTO> listInboundOrders(String status, Long warehouseId, int page, int size);
}
