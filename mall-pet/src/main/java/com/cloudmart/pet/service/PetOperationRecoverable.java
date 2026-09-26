package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.PetOperation;

/**
 * 可恢复业务操作（B01）：SPEND 类操作已扣款但本地效果未落地时，
 * 恢复任务按 rewardSnapshot 幂等补投递本地效果。
 *
 * <p>实现方必须保证幂等（重复执行不产生第二份效果）；返回 false 表示本地永久无法
 * 履约（触发按原单号派生唯一补偿单退款），异常表示暂时无法判定（保持 UNKNOWN 退避重试）。</p>
 */
public interface PetOperationRecoverable {

    /** 该实现负责的业务类型（SHOP_BUY/EVOLVE/CAREER_PROMOTE/FURNITURE_BUY） */
    String supportedBizType();

    /** @return true=本地效果已生效（可标 COMPLETED）；false=本地永久无法履约（需补偿退款） */
    boolean completePendingOperation(PetOperation operation);
}
