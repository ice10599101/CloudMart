package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateCommentRequest;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.service.ContentReviewService;
import com.cloudmart.community.service.GrowthService;
import com.cloudmart.community.service.LikeService;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.vo.CommentVO;
import com.cloudmart.community.vo.PostCommentVO;
import com.cloudmart.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostCommentServiceTest {

    @Mock
    private PostCommentMapper postCommentMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private CommunityEventProducer communityEventProducer;

    @Mock
    private GrowthService growthService;

    @Mock
    private UserEnrichmentService userEnrichmentService;

    @Mock
    private ContentReviewService contentReviewService;

    @Mock
    private LikeService likeService;

    private PostCommentServiceImpl postCommentService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long POST_ID = 100L;
    private static final Long COMMENT_ID = 200L;

    @BeforeEach
    void setUp() {
        postCommentService = new PostCommentServiceImpl(
                postCommentMapper, postMapper,
                communityEventProducer, growthService, userEnrichmentService,
                contentReviewService, likeService
        );
    }

    private Post buildPublishedPost() {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(OTHER_USER_ID);
        post.setTitle("Test Post");
        post.setContent("Post content");
        post.setStatus(1);
        post.setReviewStatus(1);
        post.setLikeCount(5);
        post.setCommentCount(2);
        post.setCollectCount(0);
        post.setShareCount(0);
        post.setViewCount(10);
        post.setIsTop(false);
        return post;
    }

    private PostComment buildComment() {
        PostComment comment = new PostComment();
        comment.setId(COMMENT_ID);
        comment.setPostId(POST_ID);
        comment.setUserId(USER_ID);
        comment.setContent("Nice post!");
        comment.setLikeCount(3);
        comment.setStatus(0);
        comment.setReviewStatus(1);
        return comment;
    }

    @Nested
    @DisplayName("createComment")
    class CreateCommentTests {

        @Test
        @DisplayName("should create comment with correct content")
        void createComment_success() {
            Post post = buildPublishedPost();
            CreateCommentRequest request = new CreateCommentRequest(
                    POST_ID, null, null, "Nice post!"
            );

            ContentReviewService.ReviewResult reviewResult = new ContentReviewService.ReviewResult(
                    true, false, "Nice post!", null
            );

            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(contentReviewService.reviewContent("Nice post!")).thenReturn(reviewResult);
            when(postCommentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
                PostComment comment = invocation.getArgument(0);
                comment.setId(COMMENT_ID);
                return 1;
            });
            when(userEnrichmentService.batchGetUsers(any(Set.class)))
                    .thenReturn(Map.of(USER_ID, new UserInfo(USER_ID, "testUser", "avatar.png", null, null)));

            PostCommentVO result = postCommentService.createComment(USER_ID, POST_ID, request);

            assertThat(result).isNotNull();
            assertThat(result.content()).isEqualTo("Nice post!");
            verify(postCommentMapper).insert(any(PostComment.class));
            verify(postMapper).updateById(post);
            verify(growthService).addExp(USER_ID, 10, "COMMENT", COMMENT_ID, "发表评论");
            verify(communityEventProducer).publishCommentEvent(OTHER_USER_ID, USER_ID, POST_ID, "Test Post", "Nice post!");
        }

        @Test
        @DisplayName("should flag comment for review when content has sensitive words")
        void createComment_reviewNeeded() {
            Post post = buildPublishedPost();
            CreateCommentRequest request = new CreateCommentRequest(
                    POST_ID, null, null, "Bad content here"
            );

            ContentReviewService.ReviewResult reviewResult = new ContentReviewService.ReviewResult(
                    true, true, "Bad content here", "contains sensitive words"
            );

            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(contentReviewService.reviewContent("Bad content here")).thenReturn(reviewResult);
            when(postCommentMapper.insert(any(PostComment.class))).thenAnswer(invocation -> {
                PostComment comment = invocation.getArgument(0);
                comment.setId(COMMENT_ID);
                assertThat(comment.getReviewStatus()).isEqualTo(0);
                return 1;
            });
            when(userEnrichmentService.batchGetUsers(any(Set.class)))
                    .thenReturn(Map.of(USER_ID, new UserInfo(USER_ID, "testUser", "avatar.png", null, null)));

            PostCommentVO result = postCommentService.createComment(USER_ID, POST_ID, request);

            assertThat(result).isNotNull();
            verify(contentReviewService).reviewContent("Bad content here");
        }
    }

    @Nested
    @DisplayName("likeComment")
    class LikeCommentTests {

        @Test
        @DisplayName("should like a comment via Redis")
        void likeComment_success() {
            PostComment comment = buildComment();
            when(postCommentMapper.selectById(COMMENT_ID)).thenReturn(comment);
            when(likeService.like(eq(USER_ID), eq("COMMENT"), eq(COMMENT_ID))).thenReturn(true);

            postCommentService.likeComment(USER_ID, COMMENT_ID);

            verify(likeService).like(USER_ID, "COMMENT", COMMENT_ID);
            verify(postCommentMapper, never()).updateById(any(PostComment.class));
        }

        @Test
        @DisplayName("should throw when already liked")
        void likeComment_alreadyLiked_throwsException() {
            PostComment comment = buildComment();
            when(postCommentMapper.selectById(COMMENT_ID)).thenReturn(comment);
            when(likeService.like(eq(USER_ID), eq("COMMENT"), eq(COMMENT_ID))).thenReturn(false);

            assertThatThrownBy(() -> postCommentService.likeComment(USER_ID, COMMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("ALREADY_LIKED");
                    });

            verify(postCommentMapper, never()).updateById(any(PostComment.class));
        }

        @Test
        @DisplayName("should throw when comment not found")
        void likeComment_commentNotFound_throwsException() {
            when(postCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

            assertThatThrownBy(() -> postCommentService.likeComment(USER_ID, COMMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("COMMENT_NOT_FOUND");
                    });

            verify(likeService, never()).like(anyLong(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("unlikeComment")
    class UnlikeCommentTests {

        @Test
        @DisplayName("should unlike a comment via Redis (idempotent)")
        void unlikeComment_success() {
            postCommentService.unlikeComment(USER_ID, COMMENT_ID);

            verify(likeService).unlike(USER_ID, "COMMENT", COMMENT_ID);
        }
    }

    @Nested
    @DisplayName("getComments")
    class GetCommentsTests {

        @Test
        @DisplayName("should batch query isLiked status to avoid N+1")
        void getComments_batchIsLiked() {
            PostComment topComment = buildComment();
            Page<PostComment> topPage = new Page<>(1, 10, 1);
            topPage.setRecords(List.of(topComment));

            when(postCommentMapper.selectPage(any(Page.class), any())).thenReturn(topPage);
            when(postCommentMapper.selectList(any())).thenReturn(List.of());
            when(userEnrichmentService.batchGetUsers(any()))
                    .thenReturn(Map.of(USER_ID, new UserInfo(USER_ID, "testUser", "avatar.png", null, null)));
            when(likeService.batchIsLiked(eq(USER_ID), eq("COMMENT"), any()))
                    .thenReturn(Map.of(COMMENT_ID, true));

            Page<PostCommentVO> result = postCommentService.getComments(POST_ID, 1, 10, USER_ID);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).isLiked()).isTrue();
            verify(likeService).batchIsLiked(eq(USER_ID), eq("COMMENT"), any());
        }

        @Test
        @DisplayName("should not query isLiked when currentUserId is null")
        void getComments_nullUserId() {
            PostComment topComment = buildComment();
            Page<PostComment> topPage = new Page<>(1, 10, 1);
            topPage.setRecords(List.of(topComment));

            when(postCommentMapper.selectPage(any(Page.class), any())).thenReturn(topPage);
            when(postCommentMapper.selectList(any())).thenReturn(List.of());
            when(userEnrichmentService.batchGetUsers(any()))
                    .thenReturn(Map.of(USER_ID, new UserInfo(USER_ID, "testUser", "avatar.png", null, null)));

            Page<PostCommentVO> result = postCommentService.getComments(POST_ID, 1, 10, null);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).isLiked()).isFalse();
            verify(likeService, never()).batchIsLiked(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("deleteComment")
    class DeleteCommentTests {

        @Test
        @DisplayName("should delete own comment")
        void deleteComment_success() {
            PostComment comment = buildComment();
            Post post = buildPublishedPost();
            when(postCommentMapper.selectById(COMMENT_ID)).thenReturn(comment);
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            postCommentService.deleteComment(USER_ID, COMMENT_ID);

            verify(postCommentMapper).deleteById(COMMENT_ID);
            verify(postMapper).updateById(post);
            assertThat(post.getCommentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw when deleting another user's comment")
        void deleteComment_notOwner_throwsException() {
            PostComment comment = buildComment();
            when(postCommentMapper.selectById(COMMENT_ID)).thenReturn(comment);

            assertThatThrownBy(() -> postCommentService.deleteComment(OTHER_USER_ID, COMMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("COMMENT_FORBIDDEN");
                    });

            verify(postCommentMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("getMyComments")
    class GetMyCommentsTests {

        @Test
        @DisplayName("should return only the user's comments")
        void getMyComments_returnsUserComments() {
            PostComment comment = buildComment();
            Page<PostComment> commentPage = new Page<>(1, 10, 1);
            commentPage.setRecords(List.of(comment));

            Post post = buildPublishedPost();

            when(postCommentMapper.selectPage(any(Page.class), any())).thenReturn(commentPage);
            when(userEnrichmentService.batchGetUsers(any())).thenReturn(Map.of());
            when(postMapper.selectById(POST_ID)).thenReturn(post);

            Page<CommentVO> result = postCommentService.getMyComments(USER_ID, 1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).content()).isEqualTo("Nice post!");
            assertThat(result.getRecords().get(0).postId()).isEqualTo(POST_ID);
        }
    }
}
