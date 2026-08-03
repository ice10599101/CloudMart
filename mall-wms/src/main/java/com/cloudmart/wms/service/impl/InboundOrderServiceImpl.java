package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundItemRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.dto.InboundOrderItemDTO;
import com.cloudmart.wms.entity.InboundOrder;
import com.cloudmart.wms.entity.InboundOrderItem;
import com.cloudmart.wms.repository.InboundOrderItemMapper;
import com.cloudmart.wms.repository.InboundOrderMapper;
import com.cloudmart.wms.service.InboundOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InboundOrderServiceImpl implements InboundOrderService {

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;

    public InboundOrderServiceImpl(InboundOrderMapper inboundOrderMapper,
                                    InboundOrderItemMapper inboundOrderItemMapper) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderItemMapper = inboundOrderItemMapper;
    }

    @Override
    @Transactional
    public InboundOrderDTO createInboundOrder(CreateInboundOrderRequest request) {
        InboundOrder order = new InboundOrder();
        order.setWarehouseId(request.warehouseId());
        order.setType(request.type());
        order.setReferenceNo(request.referenceNo());
        order.setStatus("PENDING");
        order.setTotalQuantity(request.items().stream().mapToInt(InboundItemRequest::expectedQuantity).sum());
        order.setReceivedQuantity(0);
        order.setRemark(request.remark());
        inboundOrderMapper.insert(order);

        for (InboundItemRequest item : request.items()) {
            InboundOrderItem entity = new InboundOrderItem();
            entity.setInboundOrderId(order.getId());
            entity.setSkuId(item.skuId());
            entity.setProductName(item.productName());
            entity.setExpectedQuantity(item.expectedQuantity());
            entity.setReceivedQuantity(0);
            entity.setLocationCode(item.locationCode());
            inboundOrderItemMapper.insert(entity);
        }

        return toDTO(order);
    }

    @Override
    @Transactional
    public InboundOrderDTO receiveItem(Long inboundOrderId, Long itemId, Integer receivedQuantity) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("INBOUND_ORDER_NOT_FOUND", "入库单不存在");
        }
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException("INBOUND_COMPLETED", "入库单已完成");
        }

        InboundOrderItem item = inboundOrderItemMapper.selectById(itemId);
        if (item == null || !item.getInboundOrderId().equals(inboundOrderId)) {
            throw new BusinessException("ITEM_NOT_FOUND", "入库明细不存在");
        }

        item.setReceivedQuantity(item.getReceivedQuantity() + receivedQuantity);
        inboundOrderItemMapper.updateById(item);

        order.setStatus("RECEIVING");
        order.setReceivedQuantity(order.getReceivedQuantity() + receivedQuantity);
        inboundOrderMapper.updateById(order);
        return toDTO(order);
    }

    @Override
    @Transactional
    public InboundOrderDTO completeInbound(Long inboundOrderId) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("INBOUND_ORDER_NOT_FOUND", "入库单不存在");
        }
        order.setStatus("COMPLETED");
        order.setCompletedTime(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
        return toDTO(order);
    }

    @Override
    public InboundOrderDTO getInboundOrder(Long inboundOrderId) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("INBOUND_ORDER_NOT_FOUND", "入库单不存在");
        }
        return toDTO(order);
    }

    @Override
    public IPage<InboundOrderDTO> listInboundOrders(String status, Long warehouseId, int page, int size) {
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(InboundOrder::getStatus, status);
        }
        if (warehouseId != null) {
            wrapper.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        wrapper.orderByDesc(InboundOrder::getCreatedAt);
        IPage<InboundOrder> pageResult = inboundOrderMapper.selectPage(new Page<>(page, size), wrapper);
        Page<InboundOrderDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream().map(this::toDTO).toList());
        return dtoPage;
    }

    private InboundOrderDTO toDTO(InboundOrder entity) {
        List<InboundOrderItem> items = inboundOrderItemMapper.selectList(
            new LambdaQueryWrapper<InboundOrderItem>().eq(InboundOrderItem::getInboundOrderId, entity.getId())
        );
        List<InboundOrderItemDTO> itemDTOs = items.stream()
            .map(i -> new InboundOrderItemDTO(i.getId(), i.getSkuId(), i.getProductName(),
                i.getExpectedQuantity(), i.getReceivedQuantity(), i.getLocationCode()))
            .toList();
        return new InboundOrderDTO(
            entity.getId(), entity.getWarehouseId(), entity.getType(),
            entity.getReferenceNo(), entity.getStatus(), entity.getTotalQuantity(),
            entity.getReceivedQuantity(), entity.getOperatorUserId(),
            entity.getCompletedTime(), entity.getRemark(), entity.getCreatedAt(), itemDTOs
        );
    }
}
