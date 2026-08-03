package com.cloudmart.inventory.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.inventory.dto.DeductRequest;
import com.cloudmart.inventory.dto.InventoryDTO;
import com.cloudmart.inventory.dto.ReleaseRequest;

public interface InventoryService {

    Page<InventoryDTO> listInventory(Long productId, int page, int size);

    InventoryDTO getInventory(Long skuId);

    boolean deductStock(DeductRequest request);

    void releaseStock(ReleaseRequest request);

    void confirmDeduct(Long skuId, Integer quantity, Long orderId);

    void initStock(Long skuId, Long productId, Integer stock);
}
