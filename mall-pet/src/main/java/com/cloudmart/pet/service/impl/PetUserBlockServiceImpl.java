package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.entity.PetUserBlock;
import com.cloudmart.pet.service.PetUserBlockService;
import com.cloudmart.pet.repository.PetUserBlockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户屏蔽名单实现（B14）。
 */
@Service
@Slf4j
public class PetUserBlockServiceImpl implements PetUserBlockService {

    private final PetUserBlockMapper blockMapper;

    public PetUserBlockServiceImpl(PetUserBlockMapper blockMapper) {
        this.blockMapper = blockMapper;
    }

    @Override
    @Transactional
    public void block(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            return;
        }
        PetUserBlock row = new PetUserBlock();
        row.setUserId(userId);
        row.setBlockedUserId(blockedUserId);
        try {
            blockMapper.insert(row);
        } catch (DuplicateKeyException e) {
            log.debug("重复屏蔽幂等跳过: userId={}, blocked={}", userId, blockedUserId);
        }
    }

    @Override
    @Transactional
    public void unblock(Long userId, Long blockedUserId) {
        blockMapper.delete(new LambdaQueryWrapper<PetUserBlock>()
                .eq(PetUserBlock::getUserId, userId)
                .eq(PetUserBlock::getBlockedUserId, blockedUserId));
    }

    @Override
    public List<Long> blockedUserIds(Long userId) {
        return blockMapper.selectList(new LambdaQueryWrapper<PetUserBlock>()
                        .eq(PetUserBlock::getUserId, userId))
                .stream().map(PetUserBlock::getBlockedUserId).toList();
    }

    @Override
    public boolean isBlockedEitherWay(Long userA, Long userB) {
        return blockMapper.selectCount(new LambdaQueryWrapper<PetUserBlock>()
                .and(w -> w.and(w1 -> w1.eq(PetUserBlock::getUserId, userA)
                                .eq(PetUserBlock::getBlockedUserId, userB))
                        .or().and(w2 -> w2.eq(PetUserBlock::getUserId, userB)
                                .eq(PetUserBlock::getBlockedUserId, userA)))) > 0;
    }
}
