package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreatePostRequest;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostCollection;
import com.cloudmart.community.entity.PostTag;
import com.cloudmart.community.entity.Tag;
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
import com.cloudmart.community.service.TagSubscriptionService;
import com.cloudmart.community.service.UserBlockService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostTagMapper postTagMapper;

    @Mock
    private PostCollectionMapper postCollectionMapper;

    @Mock
    private TagMapper tagMapper;

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CommunityEventProducer communityEventProducer;

    @Mock
    private GrowthService growthService;

    @Mock
    private UserEnrichmentService userEnrichmentService;

    @Mock
    private ContentReviewService contentReviewService;

    @Mock
    private UserBlockService userBlockService;

    @Mock
    private CommunityCacheService communityCacheService;

    @Mock
    private TagSubscriptionService tagSubscriptionService;

    @Mock
    private LikeService likeService;

    private PostServiceImpl postService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long POST_ID = 100L;

    @BeforeEach
    void setUp() {
        // P2-24：默认无拉黑关系（拉黑过滤用例单独覆写）
        org.mockito.Mockito.lenient().when(userBlockService.getBlockedOrBlockerIds(org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.List.of());
        postService = new PostServiceImpl(
                postMapper, postTagMapper, postCollectionMapper,
                tagMapper, userFollowMapper, objectMapper, communityEventProducer,
                growthService, userEnrichmentService, contentReviewService,
                userBlockService, communityCacheService, tagSubscriptionService,
                likeService
        );
    }

    private Post buildPublishedPost() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(USER_ID);
        post.setTitle("Test Title");
        post.setContent("Test content");
        post.setStatus(1);
        post.setReviewStatus(1);
        post.setLikeCount(5);
        post.setCommentCount(2);
        post.setCollectCount(3);
        post.setShareCount(0);
        post.setViewCount(10);
        post.setIsTop(false);
        return post;
    }

    private void mockBuildPostVODependencies(Long userId) {
        when(postTagMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userEnrichmentService.getSingleUser(anyLong()))
                .thenReturn(new UserInfo(userId, "testUser", "avatar.png", null, null));
    }

    private void mockConvertPostPageDependencies() {
        when(userEnrichmentService.batchGetUsers(any())).thenReturn(Map.of());
        when(postTagMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("createPost")
    class CreatePostTests {

        @Test
        @DisplayName("should create published post and trigger review and exp")
        void createPost_success() {
            CreatePostRequest request = new CreatePostRequest(
                    "Test Title", "Test content", null, null, "IMAGE",
                    null, null, null, 1
            );

            ContentReviewService.ReviewResult reviewResult = new ContentReviewService.ReviewResult(
                    true, false, "Test content", null
            );
            when(contentReviewService.reviewContentWithMedia(eq("Test content"), anyInt())).thenReturn(reviewResult);
            when(postMapper.insert(any(Post.class))).thenAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                post.setId(POST_ID);
                return 1;
            });
            mockBuildPostVODependencies(USER_ID);

            PostVO result = postService.createPost(USER_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.title()).isEqualTo("Test Title");
            assertThat(result.status()).isEqualTo(1);
            verify(contentReviewService).reviewContentWithMedia(eq("Test content"), anyInt());
            verify(growthService).addExp(USER_ID, 20, "POST", POST_ID, "发布帖子《Test Title》");
            verify(communityCacheService).evictFeedPosts();
        }

        @Test
        @DisplayName("should create draft without review and exp")
        void createPost_draft() {
            CreatePostRequest request = new CreatePostRequest(
                    "Draft Title", "Draft content", null, null, "IMAGE",
                    null, null, null, 0
            );

            when(postMapper.insert(any(Post.class))).thenAnswer(invocation -> {
                Post post = invocation.getArgument(0);
                post.setId(POST_ID);
                return 1;
            });
            mockBuildPostVODependencies(USER_ID);

            PostVO result = postService.createPost(USER_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.status()).isEqualTo(0);
            verify(contentReviewService, never()).reviewContentWithMedia(any(), anyInt());
            verify(contentReviewService, never()).reviewContent(any());
            verify(growthService, never()).addExp(anyLong(), any(Integer.class), any(), anyLong(), any());
        }

        @Test
        @DisplayName("should throw when title is blank")
        void createPost_titleBlank_throwsException() {
            CreatePostRequest request = new CreatePostRequest(
                    "   ", "Some content", null, null, "IMAGE",
                    null, null, null, 1
            );

            assertThatThrownBy(() -> postService.createPost(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("TITLE_BLANK");
                    });

            verify(postMapper, never()).insert(any(Post.class));
        }
    }

    @Nested
    @DisplayName("deletePost")
    class DeletePostTests {

        @Test
        @DisplayName("should delete own post")
        void deletePost_success() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postService.deletePost(USER_ID, POST_ID);

            verify(postMapper).deleteById(POST_ID);
            verify(communityCacheService).evictPostDetail(POST_ID);
            verify(communityCacheService).evictFeedPosts();
        }

        @Test
        @DisplayName("should throw when deleting another user's post")
        void deletePost_notOwner_throwsException() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            assertThatThrownBy(() -> postService.deletePost(OTHER_USER_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("POST_FORBIDDEN");
                    });

            verify(postMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("likePost")
    class LikePostTests {

        @Test
        @DisplayName("should like a post via Redis and trigger exp + event")
        void likePost_success() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(likeService.like(eq(USER_ID), eq("POST"), eq(POST_ID))).thenReturn(true);

            postService.likePost(USER_ID, POST_ID);

            verify(likeService).like(USER_ID, "POST", POST_ID);
            verify(growthService).addExp(USER_ID, 5, "LIKE_RECEIVED", POST_ID, "收到点赞");
            verify(communityEventProducer).publishLikeEvent(USER_ID, USER_ID, POST_ID, "Test Title");
        }

        @Test
        @DisplayName("should throw when liking an already liked post")
        void likePost_alreadyLiked_throwsException() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(likeService.like(eq(USER_ID), eq("POST"), eq(POST_ID))).thenReturn(false);

            assertThatThrownBy(() -> postService.likePost(USER_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("ALREADY_LIKED");
                    });

            verify(growthService, never()).addExp(anyLong(), any(Integer.class), any(), anyLong(), any());
            verify(communityEventProducer, never()).publishLikeEvent(anyLong(), anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("should throw when post not found")
        void likePost_postNotFound_throwsException() {
            when(postMapper.selectById(POST_ID)).thenReturn(null);

            assertThatThrownBy(() -> postService.likePost(USER_ID, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("POST_NOT_FOUND");
                    });

            verify(likeService, never()).like(anyLong(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("unlikePost")
    class UnlikePostTests {

        @Test
        @DisplayName("should unlike a post via Redis (idempotent)")
        void unlikePost_success() {
            postService.unlikePost(USER_ID, POST_ID);

            verify(likeService).unlike(USER_ID, "POST", POST_ID);
        }
    }

    @Nested
    @DisplayName("collectPost")
    class CollectPostTests {

        @Test
        @DisplayName("should collect a post and increment count")
        void collectPost_success() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postCollectionMapper.selectCount(any())).thenReturn(0L);

            postService.collectPost(USER_ID, POST_ID);

            verify(postCollectionMapper).insert(any(PostCollection.class));
            verify(postMapper).updateById(post);
            assertThat(post.getCollectCount()).isEqualTo(4);
            verify(communityEventProducer).publishCollectEvent(USER_ID, USER_ID, POST_ID, "Test Title");
        }
    }

    @Nested
    @DisplayName("getUserDrafts")
    class GetUserDraftsTests {

        @Test
        @DisplayName("should return only draft posts")
        void getUserDrafts_returnsOnlyDrafts() {
            Post draftPost = new Post();
            draftPost.setId(1L);
            draftPost.setUserId(USER_ID);
            draftPost.setTitle("Draft");
            draftPost.setContent("Draft content");
            draftPost.setStatus(0);
            draftPost.setReviewStatus(0);
            draftPost.setLikeCount(0);
            draftPost.setCommentCount(0);
            draftPost.setCollectCount(0);
            draftPost.setShareCount(0);
            draftPost.setViewCount(0);
            draftPost.setIsTop(false);

            Page<Post> draftPage = new Page<>(1, 10, 1);
            draftPage.setRecords(List.of(draftPost));

            when(postMapper.selectPage(any(Page.class), any())).thenReturn(draftPage);
            mockConvertPostPageDependencies();

            Page<PostVO> result = postService.getUserDrafts(USER_ID, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).status()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getLikedPosts")
    class GetLikedPostsTests {

        @Test
        @DisplayName("should return posts the user has liked")
        void getLikedPosts_returnsLikedPosts() {
            Post post = buildPublishedPost();

            when(likeService.getLikedTargetIds(eq(USER_ID), eq("POST"), eq(1), eq(10)))
                    .thenReturn(List.of(POST_ID));
            when(likeService.countLiked(eq(USER_ID), eq("POST"))).thenReturn(1L);
            when(postMapper.selectBatchIds(any())).thenReturn(List.of(post));
            mockConvertPostPageDependencies();

            Page<PostVO> result = postService.getLikedPosts(USER_ID, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).id()).isEqualTo(POST_ID);
            assertThat(result.getTotal()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return empty when user has no likes")
        void getLikedPosts_noLikes_returnsEmpty() {
            when(likeService.getLikedTargetIds(eq(USER_ID), eq("POST"), eq(1), eq(10)))
                    .thenReturn(Collections.emptyList());
            when(likeService.countLiked(eq(USER_ID), eq("POST"))).thenReturn(0L);

            Page<PostVO> result = postService.getLikedPosts(USER_ID, 1, 10);

            assertThat(result.getRecords()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("COM-01：内容可见性")
    class ContentVisibilityTests {

        private Post buildDraftPost() {
            Post post = buildPublishedPost();
            post.setStatus(0);
            post.setReviewStatus(0);
            return post;
        }

        private Post buildPendingReviewPost() {
            Post post = buildPublishedPost();
            post.setStatus(1);
            post.setReviewStatus(0);
            return post;
        }

        @Test
        @DisplayName("草稿仅作者本人可读，不写缓存、不计浏览量")
        void getPostDetail_draft_byAuthorReadable() {
            Post post = buildDraftPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            mockBuildPostVODependencies(USER_ID);
            when(likeService.isLiked(anyLong(), any(), anyLong())).thenReturn(false);
            when(postCollectionMapper.selectCount(any())).thenReturn(0L);

            PostVO vo = postService.getPostDetail(POST_ID, USER_ID);

            assertThat(vo.status()).isEqualTo(0);
            verify(postMapper, never()).update(any(), any());
            verify(communityCacheService, never()).putPostDetail(anyLong(), any());
        }

        @Test
        @DisplayName("草稿对其他用户按不存在处理（404，不泄露存在性）")
        void getPostDetail_draft_byOtherUser_notFound() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildDraftPost());

            assertThatThrownBy(() -> postService.getPostDetail(POST_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_NOT_FOUND");
        }

        @Test
        @DisplayName("草稿/待审对匿名访问按不存在处理")
        void getPostDetail_draft_anonymous_notFound() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildDraftPost());

            assertThatThrownBy(() -> postService.getPostDetail(POST_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_NOT_FOUND");

            when(postMapper.selectById(POST_ID)).thenReturn(buildPendingReviewPost());

            assertThatThrownBy(() -> postService.getPostDetail(POST_ID, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_NOT_FOUND");
        }

        @Test
        @DisplayName("待审帖对非作者不可读")
        void getPostDetail_pendingReview_byOtherUser_notFound() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPendingReviewPost());

            assertThatThrownBy(() -> postService.getPostDetail(POST_ID, OTHER_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_NOT_FOUND");
        }

        @Test
        @DisplayName("公开帖匿名访问：浏览量原子自增（不整行覆盖），仅匿名视角写缓存")
        void getPostDetail_published_anonymous_atomicViewCountAndCache() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            mockBuildPostVODependencies(USER_ID);

            PostVO vo = postService.getPostDetail(POST_ID, null);

            assertThat(vo.viewCount()).isEqualTo(11);
            // 原子自增：update(null, wrapper)，不再读改写 updateById
            verify(postMapper).update(org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.any());
            verify(postMapper, never()).updateById(any(Post.class));
            verify(communityCacheService).putPostDetail(eq(POST_ID), any());
        }

        @Test
        @DisplayName("公开帖登录访问：不写详情缓存（PostVO 含个人互动状态）")
        void getPostDetail_published_loggedIn_noCacheWrite() {
            Post post = buildPublishedPost();
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            mockBuildPostVODependencies(OTHER_USER_ID);
            when(likeService.isLiked(anyLong(), any(), anyLong())).thenReturn(false);
            when(postCollectionMapper.selectCount(any())).thenReturn(0L);

            postService.getPostDetail(POST_ID, OTHER_USER_ID);

            verify(communityCacheService, never()).putPostDetail(anyLong(), any());
        }

        @Test
        @DisplayName("作者更新不能自造状态（仅 0/1）")
        void updatePost_invalidStatus_rejected() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPublishedPost());
            com.cloudmart.community.dto.UpdatePostRequest request =
                    new com.cloudmart.community.dto.UpdatePostRequest(
                            null, null, null, null, null, null, null, null, 2);

            assertThatThrownBy(() -> postService.updatePost(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_STATUS_INVALID");
        }

        @Test
        @DisplayName("平台处置（隐藏）后的帖子不得由作者改回发布")
        void updatePost_hiddenPost_cannotRepublish() {
            Post hidden = buildPublishedPost();
            hidden.setStatus(2);
            when(postMapper.selectById(POST_ID)).thenReturn(hidden);
            com.cloudmart.community.dto.UpdatePostRequest request =
                    new com.cloudmart.community.dto.UpdatePostRequest(
                            null, null, null, null, null, null, null, null, 1);

            assertThatThrownBy(() -> postService.updatePost(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_FORBIDDEN");
        }

        @Test
        @DisplayName("审核驳回/平台处置同步清退详情与列表缓存")
        void reviewTransitions_evictCaches() {
            when(postMapper.selectById(POST_ID)).thenReturn(buildPublishedPost());

            postService.rejectPost(POST_ID, "内容违规");
            verify(communityCacheService).evictPostDetail(POST_ID);
            verify(communityCacheService).evictFeedPosts();

            // C02：updateById 行数校验需打桩
            when(postMapper.updateById(org.mockito.ArgumentMatchers.any(com.cloudmart.community.entity.Post.class)))
                    .thenReturn(1);
            postService.adminUpdatePostStatus(POST_ID, 2);
            verify(communityCacheService, org.mockito.Mockito.times(2)).evictPostDetail(POST_ID);
            verify(communityCacheService, org.mockito.Mockito.times(2)).evictFeedPosts();
        }

        @Test
        @DisplayName("C02/QA24：平台隐藏后作者两步绕过（撤草稿再发布）仍被拒绝")
        void updatePost_hiddenTwoStepBypass_rejected() {
            // 平台隐藏帖（status=2 + moderation_hidden=true）
            Post hidden = buildPublishedPost();
            hidden.setStatus(2);
            hidden.setModerationHidden(true);
            when(postMapper.selectById(POST_ID)).thenReturn(hidden);
            when(postMapper.updateById(org.mockito.ArgumentMatchers.any(com.cloudmart.community.entity.Post.class)))
                    .thenReturn(1);

            // 第一步：撤回为草稿——允许编辑保留，但处置标记保持
            when(userEnrichmentService.getSingleUser(org.mockito.ArgumentMatchers.anyLong()))
                    .thenReturn(new com.cloudmart.community.service.UserEnrichmentService.UserInfo(
                            OTHER_USER_ID, "author", null, null, null));
            when(postTagMapper.selectList(any())).thenReturn(List.of());
            com.cloudmart.community.dto.UpdatePostRequest toDraft =
                    new com.cloudmart.community.dto.UpdatePostRequest(null, null, null,
                            null, null, null, null, null, 0);
            postService.updatePost(USER_ID, POST_ID, toDraft);
            assertThat(hidden.getModerationHidden()).isTrue();

            // 第二步：草稿再发布——仍被拒绝
            com.cloudmart.community.dto.UpdatePostRequest republish =
                    new com.cloudmart.community.dto.UpdatePostRequest(null, null, null,
                            null, null, null, null, null, 1);
            assertThatThrownBy(() -> postService.updatePost(USER_ID, POST_ID, republish))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_FORBIDDEN");
            assertThat(hidden.getStatus()).isEqualTo(0);
        }

        @Test
        @DisplayName("创建帖子状态非法（自造状态）拒绝")
        void createPost_invalidStatus_rejected() {
            CreatePostRequest request = new CreatePostRequest(
                    "T", "C", null, null, "IMAGE", null, null, null, 5);

            assertThatThrownBy(() -> postService.createPost(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", "POST_STATUS_INVALID");
        }
    }
}
