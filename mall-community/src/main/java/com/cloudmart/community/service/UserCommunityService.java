package com.cloudmart.community.service;

import com.cloudmart.community.vo.UserCommunityStatsVO;
import com.cloudmart.community.vo.UserCommunityVO;

public interface UserCommunityService {

    UserCommunityVO getUserProfile(Long userId, Long currentUserId);

    /**
     * 用户社区数据面板：获赞/收到评论/浏览总量（仅统计已发布帖子）。
     * 独立端点承载，避免为聚合字段改动 {@link UserCommunityVO} 既有契约。
     */
    UserCommunityStatsVO getUserStats(Long userId);
}
