package com.cloudmart.wms.service;

import com.cloudmart.wms.dto.CreateWarehouseRequest;
import com.cloudmart.wms.dto.UpdateWarehouseRequest;
import com.cloudmart.wms.vo.WarehouseVO;

import java.util.List;

public interface WarehouseService {

    List<WarehouseVO> listWarehouses();

    WarehouseVO getWarehouse(Long id);

    WarehouseVO createWarehouse(CreateWarehouseRequest request);

    WarehouseVO updateWarehouse(Long id, UpdateWarehouseRequest request);

    void deleteWarehouse(Long id);
}
