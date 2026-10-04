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

    java.util.List<com.cloudmart.inventory.dto.ReservationScanDTO> scanReservationsForReconciliation(
            java.time.LocalDateTime since, long lastId, int limit);

    void initStock(Long skuId, Long productId, Integer stock);

    /** T19：WMS 收货入库（receiptId 幂等；PASSED 加可售并写流水），返回 {restocked, receiptId} */
    java.util.Map<String, Object> restock(String receiptId, Long skuId, int quantity);
}
