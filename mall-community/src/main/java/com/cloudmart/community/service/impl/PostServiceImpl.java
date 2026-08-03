package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
            growthService.addExp(userId, 20, "POST", post.getId(), "发布帖子");
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
            growthService.addExp(userId, 20, "POST", postId, "发布帖子");
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

        post.setViewCount(post.getViewCount() != null ? post.getViewCount() + 1 : 1);
        postMapper.updateById(post);

        PostVO result = buildPostVO(post, currentUserId);
        communityCacheService.putPostDetail(postId, result);
        return result;
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
        post.setStatus(status);
        postMapper.updateById(post);
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
        for (Long tagId : tagIds) {
            List<Long> subscriberIds = tagSubscriptionService.getSubscriberUserIds(tagId);
            for (Long subscriberId : subscriberIds) {
                if (!subscriberId.equals(authorId)) {
                    Tag tag = tagMapper.selectById(tagId);
                    String tagName = tag != null ? tag.getName() : String.valueOf(tagId);
                    communityEventProducer.publishTagNewPostEvent(subscriberId, authorId, post.getId(), post.getTitle(), tagName);
                }
            }
        }
    }
}
