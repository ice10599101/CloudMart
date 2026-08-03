package com.cloudmart.community.service;

import com.cloudmart.community.vo.UserCommunityVO;

public interface UserCommunityService {

    UserCommunityVO getUserProfile(Long userId, Long currentUserId);
}
