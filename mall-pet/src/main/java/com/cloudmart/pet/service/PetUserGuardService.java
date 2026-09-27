package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.PetUserGuard;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户级写锁服务（B01/§3.3）。
 *
 * <p>所有影响长期互斥、多宠上限、日额度组合、陪伴会话的写操作，先锁该用户行
 * （SELECT FOR UPDATE）。统一锁顺序：user guard → 活动/订单 → pet（升序）→ 钱包 →
 * 库存/进度；跨用户社交按 userId 升序锁两个 guard，避免反向死锁。</p>
 */
public interface PetUserGuardService {

    /**
     * 锁定用户守卫行（加入调用方事务；行不存在时先懒插入再锁）。
     * 同一用户的多端/并发写在此串行化。
     */
    PetUserGuard lockGuard(Long userId);
}
