package com.cloudmart.community.service;

import java.util.List;

public interface UserBlockService {

    void blockUser(Long userId, Long blockedUserId);

    void unblockUser(Long userId, Long blockedUserId);

    boolean isBlocked(Long userId, Long targetUserId);

    List<Long> getBlockedUserIds(Long userId);
}
