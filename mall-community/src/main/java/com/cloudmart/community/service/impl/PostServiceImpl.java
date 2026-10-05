package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreatePostRequest;
import com.cloudmart.community.dto.UpdatePostRequest;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostCollection;
import com.cloudmart.community.entity.PostTag;
import com.cloudmart.community.entity.Tag;
import com.cloudmart.community.entity.UserFollow;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.PostCollectionMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.PostTagMapper;
import com.cloudmart.community.repository.TagMapper;
import com.cloudmart.community.repository.UserFollowMapper;
import com.cloudmart.community.service.CommunityCacheService;
import com.cloudmart.community.service.ContentReviewService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.LikeService;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.service.TagSubscriptionService;
import com.cloudmart.community.service.UserBlockService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.TagVO;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostTagMapper postTagMapper;
    private final PostCollectionMapper postCollectionMapper;
    private final TagMapper tagMapper;
    private final UserFollowMapper userFollowMapper;
    private final ObjectMapper objectMapper;
    private final CommunityEventProducer communityEventProducer;
    private final GrowthService growthService;
    private final UserEnrichmentService userEnrichmentService;
    private final ContentReviewService contentReviewService;
    private final UserBlockService userBlockService;
    private final CommunityCacheService communityCacheService;
    private final TagSubscriptionService tagSubscriptionService;
    private final LikeService likeService;

    public PostServiceImpl(PostMapper postMapper,
                           PostTagMapper postTagMapper,
                           PostCollectionMapper postCollectionMapper,
                           TagMapper tagMapper,
                           UserFollowMapper userFollowMapper,
                           ObjectMapper objectMapper,
                           CommunityEventProducer communityEventProducer,
                           GrowthService growthService,
                           UserEnrichmentService userEnrichmentService,
                           ContentReviewService contentReviewService,
                           UserBlockService userBlockService,
                           CommunityCacheService communityCacheService,
                           TagSubscriptionService tagSubscriptionService,
                           LikeService likeService) {
        this.postMapper = postMapper;
        this.postTagMapper = postTagMapper;
        this.postCollectionMapper = postCollectionMapper;
        this.tagMapper = tagMapper;
        this.userFollowMapper = userFollowMapper;
        this.objectMapper = objectMapper;
        this.communityEventProducer = communityEventProducer;
        this.growthService = growthService;
        this.userEnrichmentService = userEnrichmentService;
        this.contentReviewService = contentReviewService;
        this.userBlockService = userBlockService;
        this.communityCacheService = communityCacheService;
        this.tagSubscriptionService = tagSubscriptionService;
        this.likeService = likeService;
    }

    @Override
    @Transactional
    public PostVO createPost(Long userId, CreatePostRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new BusinessException("TITLE_BLANK", "标题不能为空");
        }

        int status = request.status() != null ? request.status() : 1;
        // COM-01：作者只能创建草稿（0）或发布（1），不能自造其他状态
        if (status != 0 && status != 1) {
            throw new BusinessException("POST_STATUS_INVALID", "帖子状态仅支持草稿或发布");
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(request.title());
        post.setCoverImage(request.coverImage());
        post.setMediaUrls(serializeJson(request.mediaUrls()));
        post.setMediaType(request.mediaType() != null ? request.mediaType() : "IMAGE");
        post.setCategoryId(request.categoryId());
        post.setProductId(request.productId());
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCollectCount(0);
        post.setShareCount(0);
        post.setViewCount(0);
        post.setStatus(status);
        post.setIsTop(false);

        if (status == 0) {
            post.setContent(request.content());
            post.setReviewStatus(0);
        } else {
            ContentReviewService.ReviewResult reviewResult = contentReviewService.reviewContent(request.content());
            if (!reviewResult.approved()) {
                throw new BusinessException("CONTENT_REJECTED", reviewResult.reason());
            }
            post.setContent(reviewResult.filteredContent());
            post.setReviewStatus(reviewResult.needsManualReview() ? 0 : 1);
        }

        postMapper.insert(post);

        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            for (Long tagId : request.tagIds()) {
                PostTag postTag = new PostTag();
                postTag.setPostId(post.getId());
                postTag.setTagId(tagId);
                postTagMapper.insert(postTag);

                Tag tag = tagMapper.selectById(tagId);
                if (tag != null) {
                    tag.setPostCount(tag.getPostCount() != null ? tag.getPostCount() + 1 : 1);
                    tagMapper.updateById(tag);
                }
            }
        }

        if (status != 0) {
            // 经验记录带上帖子标题，避免"最近动态"里多条发布记录无法区分
            growthService.addExp(userId, 20, "POST", post.getId(), "发布帖子《" + post.getTitle() + "》");
            communityCacheService.evictFeedPosts();
            notifyTagSubscribers(userId, post, request.tagIds());
        }

        return buildPostVO(post, null);
    }

    @Override
    @Transactional
    public PostVO updatePost(Long userId, Long postId, UpdatePostRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException("POST_FORBIDDEN", "无权编辑此帖子");
        }

        int originalStatus = post.getStatus();

        if (request.title() != null) {
            post.setTitle(request.title());
        }
        if (request.content() != null) {
            if (post.getStatus() == 0 && (request.status() == null || request.status() == 0)) {
                post.setContent(request.content());
            } else {
                ContentReviewService.ReviewResult reviewResult = contentReviewService.reviewContent(request.content());
                if (!reviewResult.approved()) {
                    throw new BusinessException("CONTENT_REJECTED", reviewResult.reason());
                }
                post.setContent(reviewResult.filteredContent());
                post.setReviewStatus(reviewResult.needsManualReview() ? 0 : 1);
                post.setReviewReason(null);
            }
        }
        if (request.status() != null) {
            // COM-01：作者更新只允许草稿（0）/发布（1）之间的迁移，不能任意设置 status；
            // 平台处置状态（如隐藏）的帖子不得由作者改回发布，只能撤回为草稿
            if (request.status() != 0 && request.status() != 1) {
                throw new BusinessException("POST_STATUS_INVALID", "帖子状态仅支持草稿或发布");
            }
            if (request.status() == 1 && post.getStatus() != null
                    && post.getStatus() != 0 && post.getStatus() != 1) {
                throw new BusinessException("POST_FORBIDDEN", "该帖子已被平台处置，不能重新发布");
            }
            // C02/QA24：平台隐藏后"先撤草稿再发布"两步绕过同样拒绝——
            // moderation_hidden 只能由平台恢复发布或申诉通过清除
            if (request.status() == 1 && Boolean.TRUE.equals(post.getModerationHidden())) {
                throw new BusinessException("POST_FORBIDDEN",
                        "该帖子已被平台隐藏，如需恢复请通过申诉流程");
            }
            // 作者撤回平台隐藏帖为草稿：允许保留编辑，但处置标记不清除
            if (request.status() == 0 && post.getStatus() != null
                    && post.getStatus() != 0 && post.getStatus() != 1) {
                post.setModerationHidden(true);
            }
            if (request.status() == 1 && post.getStatus() == 0) {
                if (post.getContent() != null) {
                    ContentReviewService.ReviewResult reviewResult = contentReviewService.reviewContent(post.getContent());
                    if (!reviewResult.approved()) {
                        throw new BusinessException("CONTENT_REJECTED", reviewResult.reason());
                    }
                    post.setContent(reviewResult.filteredContent());
                    post.setReviewStatus(reviewResult.needsManualReview() ? 0 : 1);
                    post.setReviewReason(null);
                } else {
                    post.setReviewStatus(1);
                }
            }
            post.setStatus(request.status());
        }
        if (request.coverImage() != null) {
            post.setCoverImage(request.coverImage());
        }
        if (request.mediaUrls() != null) {
            post.setMediaUrls(serializeJson(request.mediaUrls()));
        }
        if (request.mediaType() != null) {
            post.setMediaType(request.mediaType());
        }
        if (request.categoryId() != null) {
            post.setCategoryId(request.categoryId());
        }
        if (request.productId() != null) {
            post.setProductId(request.productId());
        }
        postMapper.updateById(post);

        if (request.status() != null && request.status() == 1 && originalStatus == 0) {
            growthService.addExp(userId, 20, "POST", postId, "发布帖子《" + post.getTitle() + "》");
        }

        if (request.tagIds() != null) {
            List<PostTag> existingTags = postTagMapper.selectList(
                    new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId)
            );
            for (PostTag pt : existingTags) {
                Tag tag = tagMapper.selectById(pt.getTagId());
                if (tag != null && tag.getPostCount() != null && tag.getPostCount() > 0) {
                    tag.setPostCount(tag.getPostCount() - 1);
                    tagMapper.updateById(tag);
                }
            }
            postTagMapper.delete(
                    new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId)
            );

            for (Long tagId : request.tagIds()) {
                PostTag postTag = new PostTag();
                postTag.setPostId(postId);
                postTag.setTagId(tagId);
                postTagMapper.insert(postTag);

                Tag tag = tagMapper.selectById(tagId);
                if (tag != null) {
                    tag.setPostCount(tag.getPostCount() != null ? tag.getPostCount() + 1 : 1);
                    tagMapper.updateById(tag);
                }
            }
        }

        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
        return buildPostVO(post, userId);
    }

    @Override
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        if (!Objects.equals(post.getUserId(), userId)) {
            throw new BusinessException("POST_FORBIDDEN", "无权删除此帖子");
        }
        postMapper.deleteById(postId);
        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
    }

    @Override
    public PostVO getPostDetail(Long postId, Long currentUserId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }

        // COM-01：公开可见 = 已发布且审核通过；草稿/待审/驳回仅作者本人可读，
        // 其他访问者（含匿名）一律按不存在处理——不泄露内容与存在性
        boolean publiclyVisible = isPubliclyVisible(post);
        if (!publiclyVisible && !Objects.equals(post.getUserId(), currentUserId)) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }

        if (publiclyVisible) {
            // COM-01：浏览量原子自增——读改写整行 updateById 会覆盖并发的作者编辑与计数
            postMapper.update(null, new LambdaUpdateWrapper<Post>()
                    .eq(Post::getId, postId)
                    .setSql("view_count = COALESCE(view_count, 0) + 1"));
            post.setViewCount(post.getViewCount() != null ? post.getViewCount() + 1 : 1);
        }

        PostVO result = buildPostVO(post, currentUserId);
        if (publiclyVisible && currentUserId == null) {
            // COM-01：只缓存匿名视角（isLiked/isCollected 恒为 false）的公共正文——
            // PostVO 携带当前用户互动状态，按登录用户写缓存会在接回读缓存时串号
            communityCacheService.putPostDetail(postId, result);
        }
        return result;
    }

    /** 公开可见：已发布（status=1）且审核通过（reviewStatus=1） */
    private boolean isPubliclyVisible(Post post) {
        return post.getStatus() != null && post.getStatus() == 1
                && post.getReviewStatus() != null && post.getReviewStatus() == 1;
    }

    @Override
    public Page<PostVO> getFeedPosts(String tab, int page, int size, Long currentUserId) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();

        switch (tab != null ? tab : "recommend") {
            case "hot" -> wrapper.eq(Post::getStatus, 1).eq(Post::getReviewStatus, 1).orderByDesc(Post::getLikeCount);
            case "good" -> wrapper.eq(Post::getStatus, 1).eq(Post::getReviewStatus, 1).ge(Post::getLikeCount, 10).orderByDesc(Post::getCreatedAt);
            case "follow" -> {
                return new Page<>(page, size);
            }
            default -> wrapper.eq(Post::getStatus, 1).eq(Post::getReviewStatus, 1)
                    .orderByDesc(Post::getIsTop)
                    .orderByDesc(Post::getCreatedAt);
        }

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        Page<PostVO> resultPage = convertPostPage(postPage, currentUserId);

        if ("recommend".equals(tab) || tab == null) {
            List<PostVO> sorted = resultPage.getRecords().stream()
                    .sorted((a, b) -> {
                        double scoreA = computeRecommendScore(a);
                        double scoreB = computeRecommendScore(b);
                        return Double.compare(scoreB, scoreA);
                    })
                    .toList();
            resultPage.setRecords(sorted);
        }

        return resultPage;
    }

    private double computeRecommendScore(PostVO post) {
        long likeWeight = post.likeCount() != null ? post.likeCount() * 3L : 0;
        long commentWeight = post.commentCount() != null ? post.commentCount() * 2L : 0;
        long collectWeight = post.collectCount() != null ? post.collectCount() * 2L : 0;
        long viewWeight = post.viewCount() != null ? (long)(post.viewCount() * 0.01) : 0;
        long topBonus = Boolean.TRUE.equals(post.isTop()) ? 100000 : 0;

        long hoursSinceCreation = java.time.Duration.between(
                post.createdAt(), java.time.LocalDateTime.now()).toHours();
        double decayFactor = 1.0 / (1.0 + hoursSinceCreation * 0.05);

        return (likeWeight + commentWeight + collectWeight + viewWeight + topBonus) * decayFactor;
    }

    @Override
    public Page<PostVO> getUserPosts(Long userId, int page, int size, Long currentUserId) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, 1)
                .eq(Post::getReviewStatus, 1)
                .orderByDesc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, currentUserId);
    }

    @Override
    public Page<PostVO> getUserDrafts(Long userId, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getStatus, 0)
                .orderByDesc(Post::getUpdatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, userId);
    }

    @Override
    public Page<PostVO> searchPosts(String keyword, int page, int size, Long currentUserId) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, 1)
                .eq(Post::getReviewStatus, 1);

        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(Post::getTitle, keyword).or().like(Post::getContent, keyword));
        }
        wrapper.orderByDesc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, currentUserId);
    }

    @Override
    public Page<PostVO> getPostsByTag(Long tagId, int page, int size, Long currentUserId) {
        List<PostTag> postTags = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().eq(PostTag::getTagId, tagId)
        );
        if (postTags.isEmpty()) {
            return new Page<>(page, size);
        }

        List<Long> postIds = postTags.stream().map(PostTag::getPostId).toList();
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .in(Post::getId, postIds)
                .eq(Post::getStatus, 1)
                .eq(Post::getReviewStatus, 1)
                .orderByDesc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, currentUserId);
    }

    @Override
    public Page<PostVO> getFollowingFeed(Long userId, int page, int size) {
        List<UserFollow> follows = userFollowMapper.selectList(
                new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowerId, userId)
        );
        if (follows.isEmpty()) {
            return new Page<>(page, size);
        }

        List<Long> followingIds = follows.stream().map(UserFollow::getFollowingId).toList();
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .in(Post::getUserId, followingIds)
                .eq(Post::getStatus, 1)
                .eq(Post::getReviewStatus, 1)
                .orderByDesc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, userId);
    }

    @Override
    public Page<PostVO> getLikedPosts(Long userId, int page, int size) {
        List<Long> likedPostIds = likeService.getLikedTargetIds(userId, "POST", page, size);
        long total = likeService.countLiked(userId, "POST");

        if (likedPostIds.isEmpty()) {
            Page<PostVO> emptyPage = new Page<>(page, size, 0);
            emptyPage.setRecords(Collections.emptyList());
            return emptyPage;
        }

        // 批量查询帖子，保持 Redis 返回的点赞时间倒序
        List<Post> posts = postMapper.selectBatchIds(likedPostIds);
        Map<Long, Post> postMap = posts.stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> orderedPosts = likedPostIds.stream()
                .map(postMap::get)
                .filter(Objects::nonNull)
                .filter(p -> p.getStatus() == 1)
                .toList();

        Page<Post> postPage = new Page<>(page, size, total);
        postPage.setRecords(orderedPosts);
        return convertPostPage(postPage, userId);
    }

    @Override
    public Page<PostVO> getUserCollections(Long userId, int page, int size, Long currentUserId) {
        List<PostCollection> collections = postCollectionMapper.selectList(
                new LambdaQueryWrapper<PostCollection>()
                        .eq(PostCollection::getUserId, userId)
                        .orderByDesc(PostCollection::getCreatedAt)
        );
        if (collections.isEmpty()) {
            return new Page<>(page, size);
        }

        List<Long> postIds = collections.stream().map(PostCollection::getPostId).toList();
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .in(Post::getId, postIds)
                .eq(Post::getStatus, 1)
                .eq(Post::getReviewStatus, 1)
                .orderByDesc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, currentUserId);
    }

    @Override
    public void likePost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }

        boolean firstLike = likeService.like(userId, "POST", postId);
        if (!firstLike) {
            throw new BusinessException("ALREADY_LIKED", "已点赞该帖子");
        }

        growthService.addExp(post.getUserId(), 5, "LIKE_RECEIVED", postId, "收到点赞");
        communityEventProducer.publishLikeEvent(post.getUserId(), userId, postId, post.getTitle());
    }

    @Override
    public void unlikePost(Long userId, Long postId) {
        likeService.unlike(userId, "POST", postId);
    }

    @Override
    @Transactional
    public void collectPost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }

        Long existing = postCollectionMapper.selectCount(
                new LambdaQueryWrapper<PostCollection>()
                        .eq(PostCollection::getUserId, userId)
                        .eq(PostCollection::getPostId, postId)
        );
        if (existing > 0) {
            return;
        }

        PostCollection collection = new PostCollection();
        collection.setUserId(userId);
        collection.setPostId(postId);
        postCollectionMapper.insert(collection);

        post.setCollectCount(post.getCollectCount() != null ? post.getCollectCount() + 1 : 1);
        postMapper.updateById(post);

        communityEventProducer.publishCollectEvent(post.getUserId(), userId, postId, post.getTitle());
    }

    @Override
    @Transactional
    public void uncollectPost(Long userId, Long postId) {
        int deleted = postCollectionMapper.delete(
                new LambdaQueryWrapper<PostCollection>()
                        .eq(PostCollection::getUserId, userId)
                        .eq(PostCollection::getPostId, postId)
        );
        if (deleted > 0) {
            Post post = postMapper.selectById(postId);
            if (post != null && post.getCollectCount() != null && post.getCollectCount() > 0) {
                post.setCollectCount(post.getCollectCount() - 1);
                postMapper.updateById(post);
            }
        }
    }

    @Override
    public Page<PostVO> adminListPosts(String keyword, Integer status, Long userId, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Post::getTitle, keyword);
        }
        if (status != null) {
            wrapper.eq(Post::getStatus, status);
        }
        if (userId != null) {
            wrapper.eq(Post::getUserId, userId);
        }
        wrapper.orderByDesc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, null);
    }

    @Override
    @Transactional
    public void adminUpdatePostStatus(Long postId, Integer status) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        // C02/QA24：平台隐藏（status=2）置处置标记；恢复发布（status=1）清除——
        // 作者侧 updatePost 依据该标记拒绝两步绕过
        post.setStatus(status);
        post.setModerationHidden(Integer.valueOf(2).equals(status));
        int updated = postMapper.updateById(post);
        if (updated == 0) {
            throw new BusinessException("POST_STATE_CONFLICT", "帖子状态已变更，请刷新重试");
        }
        // COM-01：平台处置（隐藏/下架/恢复）同步清退详情与列表缓存
        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
    }

    @Override
    @Transactional
    public void adminToggleTop(Long postId, Boolean isTop) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        post.setIsTop(isTop);
        postMapper.updateById(post);
        // COM-01：置顶影响列表排序，同步清退详情与列表缓存
        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
    }

    @Override
    public Page<PostVO> listPendingReviewPosts(int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getReviewStatus, 0)
                .orderByAsc(Post::getCreatedAt);

        Page<Post> postPage = postMapper.selectPage(new Page<>(page, size), wrapper);
        return convertPostPage(postPage, null);
    }

    @Override
    @Transactional
    public void approvePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        post.setReviewStatus(1);
        post.setReviewReason(null);
        postMapper.updateById(post);
        // COM-01：审核通过后帖子进入公开可见，同步清退详情与列表缓存
        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
    }

    @Override
    @Transactional
    public void rejectPost(Long postId, String reason) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        post.setReviewStatus(2);
        post.setReviewReason(reason);
        postMapper.updateById(post);
        // COM-01：驳回即退出公开可见，同步清退详情与列表/搜索缓存
        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
    }

    @Override
    @Transactional
    public void adminDeletePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("POST_NOT_FOUND", "帖子不存在");
        }
        postMapper.deleteById(postId);
        communityCacheService.evictPostDetail(postId);
        communityCacheService.evictFeedPosts();
    }

    private PostVO buildPostVO(Post post, Long currentUserId) {
        List<TagVO> tags = getTagsForPost(post.getId());
        boolean isLiked = false;
        boolean isCollected = false;

        if (currentUserId != null) {
            isLiked = likeService.isLiked(currentUserId, "POST", post.getId());

            isCollected = postCollectionMapper.selectCount(
                    new LambdaQueryWrapper<PostCollection>()
                            .eq(PostCollection::getUserId, currentUserId)
                            .eq(PostCollection::getPostId, post.getId())
            ) > 0;
        }

        UserInfo author = userEnrichmentService.getSingleUser(post.getUserId());

        return new PostVO(
                post.getId(),
                post.getUserId(),
                author.nickname(),
                author.avatar(),
                post.getTitle(),
                post.getContent(),
                post.getCoverImage(),
                deserializeJson(post.getMediaUrls()),
                post.getMediaType(),
                post.getCategoryId(),
                post.getProductId(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCollectCount(),
                post.getShareCount(),
                post.getViewCount(),
                post.getStatus(),
                post.getReviewStatus(),
                post.getReviewReason(),
                post.getIsTop(),
                tags,
                isLiked,
                isCollected,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private List<TagVO> getTagsForPost(Long postId) {
        List<PostTag> postTags = postTagMapper.selectList(
                new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId)
        );
        if (postTags.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> tagIds = postTags.stream().map(PostTag::getTagId).toList();
        return tagMapper.selectBatchIds(tagIds).stream()
                .map(tag -> new TagVO(
                        tag.getId(),
                        tag.getName(),
                        tag.getIcon(),
                        tag.getPostCount(),
                        tag.getIsHot(),
                        tag.getStatus(),
                        tag.getCreatedAt()
                ))
                .toList();
    }

    private Page<PostVO> convertPostPage(Page<Post> postPage, Long currentUserId) {
        List<Post> posts = postPage.getRecords();
        Set<Long> userIds = posts.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserInfo> users = userEnrichmentService.batchGetUsers(userIds);

        // 批量查询点赞状态，避免 N+1 数据库查询
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        Map<Long, Boolean> likedMap = currentUserId != null
                ? likeService.batchIsLiked(currentUserId, "POST", postIds)
                : Collections.emptyMap();

        List<PostVO> voList = posts.stream()
                .map(post -> {
                    List<TagVO> tags = getTagsForPost(post.getId());
                    boolean isLiked = likedMap.getOrDefault(post.getId(), false);
                    boolean isCollected = false;

                    if (currentUserId != null) {
                        isCollected = postCollectionMapper.selectCount(
                                new LambdaQueryWrapper<PostCollection>()
                                        .eq(PostCollection::getUserId, currentUserId)
                                        .eq(PostCollection::getPostId, post.getId())
                        ) > 0;
                    }

                    UserInfo author = users.getOrDefault(post.getUserId(),
                            new UserInfo(post.getUserId(), "用户" + post.getUserId(), null, null, null));

                    return new PostVO(
                            post.getId(),
                            post.getUserId(),
                            author.nickname(),
                            author.avatar(),
                            post.getTitle(),
                            post.getContent(),
                            post.getCoverImage(),
                            deserializeJson(post.getMediaUrls()),
                            post.getMediaType(),
                            post.getCategoryId(),
                            post.getProductId(),
                            post.getLikeCount(),
                            post.getCommentCount(),
                            post.getCollectCount(),
                            post.getShareCount(),
                            post.getViewCount(),
                            post.getStatus(),
                            post.getReviewStatus(),
                            post.getReviewReason(),
                            post.getIsTop(),
                            tags,
                            isLiked,
                            isCollected,
                            post.getCreatedAt(),
                            post.getUpdatedAt()
                    );
                })
                .toList();

        Page<PostVO> resultPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    private String serializeJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize media URLs: {}", e.getMessage());
            return null;
        }
    }

    private List<String> deserializeJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize media URLs: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private void notifyTagSubscribers(Long authorId, Post post, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        // T27 修复：tagName 外层一次预取（原内层每个订阅者重复查同一 tag）
        Map<Long, String> tagNameById = tagMapper.selectBatchIds(tagIds).stream()
                .collect(java.util.stream.Collectors.toMap(Tag::getId, Tag::getName, (a, b) -> a));
        for (Long tagId : tagIds) {
            List<Long> subscriberIds = tagSubscriptionService.getSubscriberUserIds(tagId);
            if (subscriberIds.isEmpty()) {
                continue;
            }
            String tagName = tagNameById.getOrDefault(tagId, String.valueOf(tagId));
            for (Long subscriberId : subscriberIds) {
                if (!subscriberId.equals(authorId)) {
                    communityEventProducer.publishTagNewPostEvent(subscriberId, authorId, post.getId(), post.getTitle(), tagName);
                }
            }
        }
    }
}
