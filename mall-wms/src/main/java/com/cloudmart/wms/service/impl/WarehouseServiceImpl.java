package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.converter.WmsConverter;
import com.cloudmart.wms.dto.CreateWarehouseRequest;
import com.cloudmart.wms.dto.UpdateWarehouseRequest;
import com.cloudmart.wms.entity.Warehouse;
import com.cloudmart.wms.repository.WarehouseMapper;
import com.cloudmart.wms.service.WarehouseService;
import com.cloudmart.wms.vo.WarehouseVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseMapper warehouseMapper;
    private final WmsConverter wmsConverter;

    public WarehouseServiceImpl(WarehouseMapper warehouseMapper, WmsConverter wmsConverter) {
        this.warehouseMapper = warehouseMapper;
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
        warehouseMapper.deleteById(id);
    }
}
