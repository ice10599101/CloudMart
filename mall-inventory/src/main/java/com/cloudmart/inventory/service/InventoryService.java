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

    /**
     * TRADE-02：盘点调整单——库存唯一受控变更入口（初始化一次、补货走入库）。
     * request_key 幂等（同键同参返回原单）；乐观锁 CAS（版本冲突/负库存拒绝）；
     * 只动 available，reserved 只作快照审计；DB 提交后刷新缓存，失败仅失效（读路径自动重建）。
     */
    com.cloudmart.inventory.vo.InventoryAdjustmentVO adjustStock(
            com.cloudmart.inventory.dto.InventoryAdjustRequest request, Long operatorId);

    /** TRADE-02：调整单分页（skuId 可选，时间倒序） */
    com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.cloudmart.inventory.vo.InventoryAdjustmentVO>
            listAdjustments(Long skuId, int page, int size);
}
