package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostCollection;
import com.cloudmart.community.repository.PostCollectionMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.service.BadgeService;
import com.cloudmart.community.service.UserCommunityService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.BadgeVO;
import com.cloudmart.community.vo.UserCommunityVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserCommunityServiceImpl implements UserCommunityService {

    private final PostMapper postMapper;
    private final PostCollectionMapper postCollectionMapper;
    private final BadgeService badgeService;
    private final UserFollowService userFollowService;
    private final UserEnrichmentService userEnrichmentService;

    public UserCommunityServiceImpl(PostMapper postMapper,
                                    PostCollectionMapper postCollectionMapper,
                                    BadgeService badgeService,
                                    UserFollowService userFollowService,
                                    UserEnrichmentService userEnrichmentService) {
        this.postMapper = postMapper;
        this.postCollectionMapper = postCollectionMapper;
        this.badgeService = badgeService;
        this.userFollowService = userFollowService;
        this.userEnrichmentService = userEnrichmentService;
    }

    @Override
    public UserCommunityVO getUserProfile(Long userId, Long currentUserId) {
        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .eq(Post::getStatus, 1)
        );

        long followCount = userFollowService.getFollowCount(userId);
        long followerCount = userFollowService.getFollowerCount(userId);

        Long collectCount = postCollectionMapper.selectCount(
                new LambdaQueryWrapper<PostCollection>()
                        .eq(PostCollection::getUserId, userId)
        );

        List<BadgeVO> badges = badgeService.getUserBadges(userId);

        UserInfo userInfo = userEnrichmentService.getSingleUser(userId);

        // 填充当前用户的关注状态（null 会导致前端关注按钮状态不同步，无法取关）；
        // 未登录或查看自己主页时保持 null（自己无关注语义）
        Boolean isFollowed = (currentUserId == null || currentUserId.equals(userId))
                ? null
                : userFollowService.isFollowing(currentUserId, userId);

        return new UserCommunityVO(
                userId,
                userInfo.nickname(),
                userInfo.avatar(),
                userInfo.signature(),
                postCount,
                followCount,
                followerCount,
                collectCount,
                badges,
                isFollowed
        );
    }
}
