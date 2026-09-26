package com.cloudmart.pet.service;

import java.util.List;

/**
 * 用户屏蔽名单（B14）：屏蔽后双方不能新增拜访收益、挑战、留言或申请；
 * 已存在数据按权限继续保留（审计不删除）。
 */
public interface PetUserBlockService {

    /** 屏蔽（幂等：重复屏蔽不报错） */
    void block(Long userId, Long blockedUserId);

    /** 取消屏蔽（幂等） */
    void unblock(Long userId, Long blockedUserId);

    /** 我屏蔽的名单 */
    List<Long> blockedUserIds(Long userId);

    /** 双向屏蔽判定：任一方屏蔽对方即视为已屏蔽 */
    boolean isBlockedEitherWay(Long userA, Long userB);
}
