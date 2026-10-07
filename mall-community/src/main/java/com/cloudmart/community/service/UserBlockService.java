package com.cloudmart.community.service;

import java.util.List;

public interface UserBlockService {

    void blockUser(Long userId, Long blockedUserId);

    void unblockUser(Long userId, Long blockedUserId);

    boolean isBlocked(Long userId, Long targetUserId);

    List<Long> getBlockedUserIds(Long userId);

    /**
     * P2-24：内容过滤视角的双向拉黑集合——我拉黑的 ∪ 拉黑我的。
     * feed/搜索/评论/主页等读链路据此过滤（与 pet 模块 isBlockedEitherWay 同语义）。
     */
    List<Long> getBlockedOrBlockerIds(Long userId);
}
