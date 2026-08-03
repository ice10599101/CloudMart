package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.UserFollow;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.UserFollowMapper;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.UserCommunityVO;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserFollowServiceImpl implements UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final CommunityEventProducer communityEventProducer;
    private final GrowthService growthService;
    private final UserEnrichmentService userEnrichmentService;

    public UserFollowServiceImpl(UserFollowMapper userFollowMapper,
                                 CommunityEventProducer communityEventProducer,
                                 GrowthService growthService,
                                 UserEnrichmentService userEnrichmentService) {
        this.userFollowMapper = userFollowMapper;
        this.communityEventProducer = communityEventProducer;
        this.growthService = growthService;
        this.userEnrichmentService = userEnrichmentService;
    }

    @Override
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (Objects.equals(followerId, followingId)) {
            throw new BusinessException("CANNOT_FOLLOW_SELF", "不能关注自己");
        }

        Long existing = userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFollowingId, followingId)
        );
        if (existing > 0) {
            throw new BusinessException("ALREADY_FOLLOWING", "已关注该用户");
        }

        UserFollow userFollow = new UserFollow();
        userFollow.setFollowerId(followerId);
        userFollow.setFollowingId(followingId);
        userFollowMapper.insert(userFollow);

        growthService.addExp(followingId, 15, "FOLLOW_RECEIVED", null, "获得新粉丝");
        communityEventProducer.publishFollowEvent(followingId, followerId);
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        userFollowMapper.delete(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFollowingId, followingId)
        );
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, followerId)
                        .eq(UserFollow::getFollowingId, followingId)
        ) > 0;
    }

    @Override
    public long getFollowCount(Long userId) {
        return userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
        );
    }

    @Override
    public long getFollowerCount(Long userId) {
        return userFollowMapper.selectCount(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowingId, userId)
        );
    }

    @Override
    public List<UserCommunityVO> getFollowerList(Long userId, Long currentUserId, int page, int size) {
        List<UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowingId, userId)
                        .orderByDesc(UserFollow::getCreatedAt)
        );
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }

        return follows.stream()
                .map(follow -> {
                    boolean isFollowed = currentUserId != null && isFollowing(currentUserId, follow.getFollowerId());
                    return new UserCommunityVO(
                            follow.getFollowerId(),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            isFollowed
                    );
                })
                .toList();
    }

    @Override
    public List<UserCommunityVO> getFollowingList(Long userId, Long currentUserId, int page, int size) {
        List<UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getCreatedAt)
        );
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }

        return follows.stream()
                .map(follow -> new UserCommunityVO(
                        follow.getFollowingId(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true
                ))
                .toList();
    }

    @Override
    public List<UserCommunityVO> getRecommendedUsers(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);

        if (userId == null) {
            List<UserFollow> topFollowed = userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>()
                            .select(UserFollow::getFollowingId)
                            .groupBy(UserFollow::getFollowingId)
                            .orderByDesc(UserFollow::getFollowingId)
                            .last("LIMIT " + safeLimit));
            List<Long> ids = topFollowed.stream()
                    .map(UserFollow::getFollowingId)
                    .distinct()
                    .toList();
            if (ids.isEmpty()) return List.of();
            Map<Long, UserEnrichmentService.UserInfo> userMap = userEnrichmentService.batchGetUsers(new HashSet<>(ids));
            return ids.stream()
                    .map(id -> {
                        UserEnrichmentService.UserInfo info = userMap.getOrDefault(id, new UserEnrichmentService.UserInfo(id, "用户" + id, null, null, null));
                        return new UserCommunityVO(id, info.nickname(), info.avatar(), info.signature(), null, null, null, null, null, null);
                    })
                    .toList();
        }

        List<UserFollow> myFollows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId));
        Set<Long> followingIds = myFollows.stream()
                .map(UserFollow::getFollowingId)
                .collect(Collectors.toSet());
        followingIds.add(userId);

        Map<Long, Integer> mutualCountMap = new HashMap<>();
        for (UserFollow myFollow : myFollows) {
            List<UserFollow> theirFollows = userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, myFollow.getFollowingId()));
            for (UserFollow theirFollow : theirFollows) {
                Long candidateId = theirFollow.getFollowingId();
                if (!followingIds.contains(candidateId)) {
                    mutualCountMap.merge(candidateId, 1, Integer::sum);
                }
            }
        }

        List<Long> recommendedIds = mutualCountMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .limit(safeLimit)
                .map(Map.Entry::getKey)
                .toList();

        if (recommendedIds.size() < safeLimit) {
            int remaining = safeLimit - recommendedIds.size();
            Set<Long> excludeIds = new HashSet<>(followingIds);
            excludeIds.addAll(recommendedIds);

            List<UserFollow> topFollowed = userFollowMapper.selectList(
                    new LambdaQueryWrapper<UserFollow>()
                            .select(UserFollow::getFollowingId)
                            .notIn(UserFollow::getFollowingId, excludeIds)
                            .groupBy(UserFollow::getFollowingId)
                            .orderByDesc(UserFollow::getFollowingId)
                            .last("LIMIT " + remaining));

            List<Long> hotUserIds = topFollowed.stream()
                    .map(UserFollow::getFollowingId)
                    .filter(id -> !excludeIds.contains(id))
                    .toList();
            recommendedIds = new ArrayList<>(recommendedIds);
            recommendedIds.addAll(hotUserIds);
        }

        if (recommendedIds.isEmpty()) {
            return List.of();
        }

        Map<Long, UserInfo> userMap = userEnrichmentService.batchGetUsers(new HashSet<>(recommendedIds));

        return recommendedIds.stream()
                .map(id -> {
                    UserInfo user = userMap.getOrDefault(id, new UserInfo(id, "未知用户", "", null, null));
                    boolean isFollowed = followingIds.contains(id) && !id.equals(userId);
                    return new UserCommunityVO(
                            id,
                            user.nickname(),
                            user.avatar(),
                            user.signature(),
                            null,
                            null,
                            null,
                            mutualCountMap.getOrDefault(id, 0).longValue(),
                            null,
                            isFollowed
                    );
                })
                .toList();
    }
}
