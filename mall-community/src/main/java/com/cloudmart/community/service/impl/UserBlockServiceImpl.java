package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.UserBlock;
import com.cloudmart.community.repository.UserBlockMapper;
import com.cloudmart.community.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserBlockServiceImpl implements UserBlockService {

    private final UserBlockMapper userBlockMapper;

    @Override
    public void blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BusinessException("INVALID_BLOCK", "不能拉黑自己");
        }

        UserBlock existing = userBlockMapper.selectOne(
                new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)
                        .eq(UserBlock::getBlockedUserId, blockedUserId));
        if (existing != null) {
            return;
        }

        UserBlock block = new UserBlock();
        block.setUserId(userId);
        block.setBlockedUserId(blockedUserId);
        userBlockMapper.insert(block);
    }

    @Override
    public void unblockUser(Long userId, Long blockedUserId) {
        userBlockMapper.delete(
                new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)
                        .eq(UserBlock::getBlockedUserId, blockedUserId));
    }

    @Override
    public boolean isBlocked(Long userId, Long targetUserId) {
        return userBlockMapper.selectCount(
                new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)
                        .eq(UserBlock::getBlockedUserId, targetUserId)) > 0;
    }

    @Override
    public List<Long> getBlockedUserIds(Long userId) {
        List<UserBlock> blocks = userBlockMapper.selectList(
                new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId)
                        .select(UserBlock::getBlockedUserId));
        return blocks.stream().map(UserBlock::getBlockedUserId).toList();
    }
}
