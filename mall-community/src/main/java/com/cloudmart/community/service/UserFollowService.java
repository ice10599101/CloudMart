package com.cloudmart.community.service;

import com.cloudmart.community.vo.UserCommunityVO;

import java.util.List;

public interface UserFollowService {

    void follow(Long followerId, Long followingId);

    void unfollow(Long followerId, Long followingId);

    boolean isFollowing(Long followerId, Long followingId);

    long getFollowCount(Long userId);

    long getFollowerCount(Long userId);

    List<UserCommunityVO> getFollowerList(Long userId, Long currentUserId, int page, int size);

    List<UserCommunityVO> getFollowingList(Long userId, Long currentUserId, int page, int size);

    List<UserCommunityVO> getRecommendedUsers(Long userId, int limit);
}
