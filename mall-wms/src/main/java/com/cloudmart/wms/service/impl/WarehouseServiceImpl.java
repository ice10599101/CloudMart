package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateWarehouseRequest;
import com.cloudmart.wms.dto.UpdateWarehouseRequest;
import com.cloudmart.wms.entity.InboundOrder;
import com.cloudmart.wms.entity.PickOrder;
import com.cloudmart.wms.entity.ShippingOrder;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.repository.InboundOrderMapper;
import com.cloudmart.wms.repository.PickOrderMapper;
import com.cloudmart.wms.repository.ShippingOrderMapper;
import com.cloudmart.wms.repository.WarehouseMapper;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.WarehouseVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final InboundOrderMapper inboundOrderMapper;
    private final PickOrderMapper pickOrderMapper;
    private final ShippingOrderMapper shippingOrderMapper;
    private final WmsConverter wmsConverter;

    public WarehouseServiceImpl(WarehouseMapper warehouseMapper,
                                InboundOrderMapper inboundOrderMapper,
                                PickOrderMapper pickOrderMapper,
                                ShippingOrderMapper shippingOrderMapper,
                                WmsConverter wmsConverter) {
        this.warehouseMapper = warehouseMapper;
        this.inboundOrderMapper = inboundOrderMapper;
        this.pickOrderMapper = pickOrderMapper;
        this.shippingOrderMapper = shippingOrderMapper;
        this.wmsConverter = wmsConverter;
    }

    @Override
    public List<WarehouseVO> listWarehouses() {
        List<Warehouse> warehouses = warehouseMapper.selectList(null);
        return wmsConverter.toWarehouseVOList(warehouses);
    }

    @Override
    public WarehouseVO getWarehouse(Long id) {
        Warehouse warehouse = warehouseMapper.selectById(id);
        if (warehouse == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        return wmsConverter.toWarehouseVO(warehouse);
    }

    @Override
    public WarehouseVO createWarehouse(CreateWarehouseRequest request) {
        Warehouse entity = new Warehouse();
        entity.setName(request.name());
        entity.setAddress(request.address());
        entity.setContactPhone(request.contactPhone());
        entity.setStatus(request.status() != null ? request.status() : 0);
        warehouseMapper.insert(entity);
        return wmsConverter.toWarehouseVO(entity);
    }

    @Override
    public WarehouseVO updateWarehouse(Long id, UpdateWarehouseRequest request) {
        Warehouse existing = warehouseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        // TRADE-03：停用（1→0）前校验未完成单据；有在办业务的仓库不允许停用
        boolean disabling = request.status() != null && request.status() == 0
                && (existing.getStatus() == null || existing.getStatus() != 0);
        if (disabling && hasOpenDocuments(id)) {
            throw new BusinessException("WAREHOUSE_HAS_OPEN_DOCUMENTS",
                    "该仓库存在未完成的入库/拣货/物流单据，请先完成或转运单后再停用");
        }
        if (request.name() != null) {
            existing.setName(request.name());
        }
        if (request.address() != null) {
            existing.setAddress(request.address());
        }
        if (request.contactPhone() != null) {
            existing.setContactPhone(request.contactPhone());
        }
        if (request.status() != null) {
            existing.setStatus(request.status());
        }
        warehouseMapper.updateById(existing);
        return wmsConverter.toWarehouseVO(existing);
    }

    @Override
    public void deleteWarehouse(Long id) {
        Warehouse existing = warehouseMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("WAREHOUSE_NOT_FOUND", "仓库不存在");
        }
        // TRADE-03：有历史引用的仓库不允许物理删除——一律先停用（履约历史需要可追溯）
        if (hasOpenDocuments(id)) {
            throw new BusinessException("WAREHOUSE_HAS_OPEN_DOCUMENTS",
                    "该仓库存在未完成的入库/拣货/物流单据，不能删除；请改用停用");
        }
        warehouseMapper.deleteById(id);
    }

    /** 未完成单据：RECEIVING/未完成入库 + 未终态拣货单 + 未终态物流单 */
    private boolean hasOpenDocuments(Long warehouseId) {
        Long receivingInbound = inboundOrderMapper.selectCount(
                new LambdaQueryWrapper<InboundOrder>()
                        .eq(InboundOrder::getWarehouseId, warehouseId)
                        .ne(InboundOrder::getStatus, "COMPLETED"));
        if (receivingInbound != null && receivingInbound > 0) {
            return true;
        }
        Long openPick = pickOrderMapper.selectCount(
                new LambdaQueryWrapper<PickOrder>()
                        .eq(PickOrder::getWarehouseId, warehouseId)
                        .notIn(PickOrder::getStatus, List.of("PACKED")));
        if (openPick != null && openPick > 0) {
            return true;
        }
        Long openShipping = shippingOrderMapper.selectCount(
                new LambdaQueryWrapper<ShippingOrder>()
                        .eq(ShippingOrder::getWarehouseId, warehouseId)
                        .notIn(ShippingOrder::getStatus, List.of("DELIVERED")));
        return openShipping != null && openShipping > 0;
    }
}
