package com.cloudmart.inventory.service;

import com.cloudmart.inventory.dto.TccDeductRequest;

/**
 * 库存 TCC 模式服务接口（Seata TCC 框架集成版）。
 * <p>
 * 通过 Seata @TwoPhaseBusinessAction 注解，由 Seata TC 统一管理 TCC 事务生命周期，
 * 无需手动编排 xid / confirm / cancel 调用。
 * <p>
 * Try: 冻结库存（Redis Lua 原子预扣）
 * Confirm: 确认扣减（更新 DB + 清理 Redis 冻结记录）
 * Cancel: 释放冻结（Redis 归还 + 清理冻结记录）
 */
public interface InventoryTccService {

    /**
     * Try: 冻结库存。由 Seata TCC 框架自动生成 xid 并管理事务分支。
     *
     * @param request 扣减请求（含 skuId、productId、quantity）
     * @return 冻结记录 xid
     */
    String tryDeduct(TccDeductRequest request);

    /**
     * Confirm: 确认扣减冻结的库存。由 Seata TCC 框架在全局事务提交时自动调用。
     *
     * @param xid 事务分支 ID（BusinessActionContext 中的 xid）
     */
    boolean confirmDeduct(String xid);

    /**
     * Cancel: 取消冻结，归还库存。由 Seata TCC 框架在全局事务回滚时自动调用。
     *
     * @param xid 事务分支 ID（BusinessActionContext 中的 xid）
     */
    boolean cancelDeduct(String xid);
}
