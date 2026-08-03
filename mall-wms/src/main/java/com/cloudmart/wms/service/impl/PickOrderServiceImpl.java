package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.dto.CreatePickOrderRequest;
import com.cloudmart.wms.dto.PickOrderDTO;
import com.cloudmart.wms.dto.PickOrderItemDTO;
import com.cloudmart.wms.entity.PickOrder;
import com.cloudmart.wms.entity.PickOrderItem;
import com.cloudmart.wms.repository.PickOrderItemMapper;
import com.cloudmart.wms.repository.PickOrderMapper;
import com.cloudmart.wms.service.PickOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PickOrderServiceImpl implements PickOrderService {

    private final PickOrderMapper pickOrderMapper;
    private final PickOrderItemMapper pickOrderItemMapper;

    public PickOrderServiceImpl(PickOrderMapper pickOrderMapper, PickOrderItemMapper pickOrderItemMapper) {
        this.pickOrderMapper = pickOrderMapper;
        this.pickOrderItemMapper = pickOrderItemMapper;
    }

    @Override
    @Transactional
    public PickOrderDTO createPickOrder(CreatePickOrderRequest request) {
        PickOrder existing = pickOrderMapper.selectOne(
            new LambdaQueryWrapper<PickOrder>().eq(PickOrder::getOrderId, request.orderId())
        );
        if (existing != null) {
            throw new BusinessException("PICK_ORDER_EXISTS", "该订单已有拣货单");
        }

        PickOrder pickOrder = new PickOrder();
        pickOrder.setOrderId(request.orderId());
        pickOrder.setWarehouseId(request.warehouseId());
        pickOrder.setStatus("PENDING");
        pickOrder.setRemark(request.remark());
        pickOrderMapper.insert(pickOrder);
        return toDTO(pickOrder);
    }

    @Override
    @Transactional
    public PickOrderDTO startPick(Long pickOrderId, Long assignedUserId) {
        PickOrder pickOrder = pickOrderMapper.selectById(pickOrderId);
        if (pickOrder == null) {
            throw new BusinessException("PICK_ORDER_NOT_FOUND", "拣货单不存在");
        }
        if (!"PENDING".equals(pickOrder.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "只有待拣货状态才能开始拣货");
        }
        pickOrder.setStatus("PICKING");
        pickOrder.setAssignedUserId(assignedUserId);
        pickOrder.setPickTime(LocalDateTime.now());
        pickOrderMapper.updateById(pickOrder);
        return toDTO(pickOrder);
    }

    @Override
    @Transactional
    public PickOrderDTO confirmPicked(Long pickOrderId) {
        PickOrder pickOrder = pickOrderMapper.selectById(pickOrderId);
        if (pickOrder == null) {
            throw new BusinessException("PICK_ORDER_NOT_FOUND", "拣货单不存在");
        }
        if (!"PICKING".equals(pickOrder.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "只有拣货中状态才能确认拣货完成");
        }
        pickOrder.setStatus("PICKED");
        pickOrderMapper.updateById(pickOrder);
        return toDTO(pickOrder);
    }

    @Override
    @Transactional
    public PickOrderDTO confirmPacked(Long pickOrderId) {
        PickOrder pickOrder = pickOrderMapper.selectById(pickOrderId);
        if (pickOrder == null) {
            throw new BusinessException("PICK_ORDER_NOT_FOUND", "拣货单不存在");
        }
        if (!"PICKED".equals(pickOrder.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "只有已拣货状态才能确认打包完成");
        }
        pickOrder.setStatus("PACKED");
        pickOrder.setPackedTime(LocalDateTime.now());
        pickOrderMapper.updateById(pickOrder);
        return toDTO(pickOrder);
    }

    @Override
    public PickOrderDTO getPickOrder(Long pickOrderId) {
        PickOrder pickOrder = pickOrderMapper.selectById(pickOrderId);
        if (pickOrder == null) {
            throw new BusinessException("PICK_ORDER_NOT_FOUND", "拣货单不存在");
        }
        return toDTO(pickOrder);
    }

    @Override
    public IPage<PickOrderDTO> listPickOrders(String status, Long warehouseId, int page, int size) {
        LambdaQueryWrapper<PickOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(PickOrder::getStatus, status);
        }
        if (warehouseId != null) {
            wrapper.eq(PickOrder::getWarehouseId, warehouseId);
        }
        wrapper.orderByDesc(PickOrder::getCreatedAt);
        IPage<PickOrder> pageResult = pickOrderMapper.selectPage(new Page<>(page, size), wrapper);
        Page<PickOrderDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream().map(this::toDTO).toList());
        return dtoPage;
    }

    @Override
    public PickOrderDTO findByOrderId(Long orderId) {
        PickOrder pickOrder = pickOrderMapper.selectOne(
            new LambdaQueryWrapper<PickOrder>().eq(PickOrder::getOrderId, orderId)
        );
        if (pickOrder == null) {
            return null;
        }
        return toDTO(pickOrder);
    }

    private PickOrderDTO toDTO(PickOrder entity) {
        List<PickOrderItem> items = pickOrderItemMapper.selectList(
            new LambdaQueryWrapper<PickOrderItem>().eq(PickOrderItem::getPickOrderId, entity.getId())
        );
        List<PickOrderItemDTO> itemDTOs = items.stream()
            .map(i -> new PickOrderItemDTO(i.getId(), i.getSkuId(), i.getProductName(),
                i.getSkuAttributes(), i.getQuantity(), i.getLocationCode(), i.getPickedQuantity()))
            .toList();
        return new PickOrderDTO(
            entity.getId(), entity.getOrderId(), entity.getWarehouseId(),
            entity.getStatus(), entity.getAssignedUserId(), entity.getPickTime(),
            entity.getPackedTime(), entity.getRemark(), entity.getCreatedAt(), itemDTOs
        );
    }
}
