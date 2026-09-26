package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.enums.ResourceLogSource;
import com.cloudmart.wish.service.PetSupportService;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.vo.PetWalletOperationVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 宠物星光支持服务实现：为内部端点提供事务边界（原 UserStatService 方法为 MANDATORY）。
 *
 * <p>幂等变体（B01）：去重记录 + 余额变更 + 流水写入同一事务提交；
 * 重复请求按 operationId 返回原结果，失败整体回滚可按原单重试。</p>
 */
@Service
public class PetSupportServiceImpl implements PetSupportService {

    private final UserStatService userStatService;

    public PetSupportServiceImpl(UserStatService userStatService) {
        this.userStatService = userStatService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int earnForPet(Long userId, int amount, Long refId) {
        return userStatService.earnStarlight(userId, amount, ResourceLogSource.PET_REWARD, refId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int spendForPet(Long userId, int cost, Long refId) {
        return userStatService.spendStarlight(userId, cost, ResourceLogSource.PET_SHOP, refId);
    }

    @Override
    public int petStarlightBalance(Long userId) {
        return userStatService.getStarlightBalance(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetWalletOperationVO earnForPetIdempotent(Long userId, int amount, Long refId, String operationId) {
        return userStatService.earnStarlightIdempotent(userId, amount, ResourceLogSource.PET_REWARD, refId, operationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PetWalletOperationVO spendForPetIdempotent(Long userId, int cost, Long refId, String operationId) {
        return userStatService.spendStarlightIdempotent(userId, cost, ResourceLogSource.PET_SHOP, refId, operationId);
    }

    @Override
    public PetWalletOperationVO findPetOperation(String operationId) {
        return userStatService.findOperation(operationId);
    }
}
