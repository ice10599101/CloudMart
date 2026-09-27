package com.cloudmart.pet.wallet.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetWalletAccount;
import com.cloudmart.pet.entity.PetWalletTransaction;
import com.cloudmart.pet.repository.PetWalletAccountMapper;
import com.cloudmart.pet.repository.PetWalletTransactionMapper;
import com.cloudmart.pet.wallet.PetWalletQueryService;
import com.cloudmart.pet.wallet.PetWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 宠物币钱包查询服务实现（W01）：只读，无副作用。
 */
@Service
@RequiredArgsConstructor
public class PetWalletQueryServiceImpl implements PetWalletQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PetWalletAccountMapper accountMapper;
    private final PetWalletTransactionMapper transactionMapper;
    private final PetWalletService walletService;

    @Override
    @Transactional(readOnly = true)
    public PetWalletAccount getWallet(Long userId) {
        PetWalletAccount account = accountMapper.selectOne(new LambdaQueryWrapper<PetWalletAccount>()
                .eq(PetWalletAccount::getUserId, userId)
                .eq(PetWalletAccount::getCurrency, PetWalletServiceImpl.CURRENCY_PET_COIN));
        if (account != null) {
            return account;
        }
        // 懒创建期初 0 账户（无宠物也可读本人账户，§8.2）
        return walletService.getOrCreateAccount(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PetWalletTransaction> listTransactions(Long userId, Long cursor, int size,
                                                       String direction, String bizType) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        LambdaQueryWrapper<PetWalletTransaction> wrapper = new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getUserId, userId)
                .orderByDesc(PetWalletTransaction::getId)
                .last("LIMIT " + safeSize);
        if (cursor != null) {
            wrapper.lt(PetWalletTransaction::getId, cursor);
        }
        if (direction != null && !direction.isBlank()) {
            wrapper.eq(PetWalletTransaction::getDirection, direction.toUpperCase());
        }
        if (bizType != null && !bizType.isBlank()) {
            wrapper.eq(PetWalletTransaction::getBizType, bizType.toUpperCase());
        }
        return transactionMapper.selectList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PetWalletTransaction> listRefundsOf(Long originalTransactionId) {
        return transactionMapper.selectList(new LambdaQueryWrapper<PetWalletTransaction>()
                .eq(PetWalletTransaction::getOriginalTransactionId, originalTransactionId)
                .orderByDesc(PetWalletTransaction::getId));
    }
}
