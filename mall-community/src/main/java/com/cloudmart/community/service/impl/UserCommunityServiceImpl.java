package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostCollection;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.entity.PostLike;
import com.cloudmart.community.repository.PostCollectionMapper;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostLikeMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.service.BadgeService;
import com.cloudmart.community.service.UserCommunityService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.BadgeVO;
import com.cloudmart.community.vo.UserCommunityStatsVO;
import com.cloudmart.community.vo.UserCommunityVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserCommunityServiceImpl implements UserCommunityService {

    private final PostMapper postMapper;
    private final PostCollectionMapper postCollectionMapper;
    private final PostCommentMapper postCommentMapper;
    private final PostLikeMapper postLikeMapper;
    private final BadgeService badgeService;
    private final UserFollowService userFollowService;
    private final UserEnrichmentService userEnrichmentService;

    public UserCommunityServiceImpl(PostMapper postMapper,
                                    PostCollectionMapper postCollectionMapper,
                                    PostCommentMapper postCommentMapper,
                                    PostLikeMapper postLikeMapper,
                                    BadgeService badgeService,
                                    UserFollowService userFollowService,
                                    UserEnrichmentService userEnrichmentService) {
        this.postMapper = postMapper;
        this.postCollectionMapper = postCollectionMapper;
        this.postCommentMapper = postCommentMapper;
        this.postLikeMapper = postLikeMapper;
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
                isFollowed,
                null
        );
    }

    @Override
    public UserCommunityStatsVO getUserStats(Long userId) {
        // 获赞：他人给 TA 已发布帖子点的赞总数（COALESCE 保证无数据时返回 0，无用户输入参与，无注入面）
        List<java.util.Map<String, Object>> likeRows = postMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Post>()
                        .select("COALESCE(SUM(like_count), 0) AS likes_received")
                        .eq("user_id", userId)
                        .eq("status", 1));
        Long likesReceived = toLong(likeRows == null || likeRows.isEmpty() ? null : likeRows.get(0).get("likes_received"));

        // TA 的评论 / TA 赞过：按用户维度的行为计数
        Long commentsMade = postCommentMapper.selectCount(
                new LambdaQueryWrapper<PostComment>().eq(PostComment::getUserId, userId));
        Long likesGiven = postLikeMapper.selectCount(
                new LambdaQueryWrapper<PostLike>().eq(PostLike::getUserId, userId));

        return new UserCommunityStatsVO(likesReceived, commentsMade, likesGiven);
    }

    /** 聚合结果兼容 Number/null（SUM 在部分驱动下返回 BigDecimal/Long） */
    private Long toLong(Object value) {
        if (value == null) return 0L;
        return value instanceof Number number ? number.longValue() : Long.parseLong(value.toString());
    }
}
