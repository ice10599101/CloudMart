package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetUserGuard;
import com.cloudmart.pet.repository.PetUserGuardMapper;
import com.cloudmart.pet.service.PetUserGuardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户级写锁服务实现（B01）。
 *
 * <p>REQUIRED 传播：加入调用方业务事务——锁随事务持有/释放，事务回滚自动释放。
 * 懒插入采用"插入失败即重读"策略；FOR UPDATE 保证同一用户写路径串行化。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetUserGuardServiceImpl implements PetUserGuardService {

    private final PetUserGuardMapper guardMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public PetUserGuard lockGuard(Long userId) {
        PetUserGuard guard = guardMapper.selectOne(new LambdaQueryWrapper<PetUserGuard>()
                .eq(PetUserGuard::getUserId, userId)
                .last("FOR UPDATE"));
        if (guard != null) {
            return guard;
        }
        PetUserGuard fresh = new PetUserGuard();
        fresh.setUserId(userId);
        fresh.setVersion(0L);
        try {
            guardMapper.insert(fresh);
        } catch (DuplicateKeyException e) {
            // 并发懒插入：另一事务已建行，重读加锁（其提交后本事务可见）
            log.debug("用户守卫行并发插入, userId={}", userId);
        }
        PetUserGuard locked = guardMapper.selectOne(new LambdaQueryWrapper<PetUserGuard>()
                .eq(PetUserGuard::getUserId, userId)
                .last("FOR UPDATE"));
        if (locked == null) {
            // 理论不可达（插入已成功或冲突方已提交）；防御性失败快失败
            throw new IllegalStateException("用户守卫行不可读: " + userId);
        }
        return locked;
    }
}
