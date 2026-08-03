package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostShare;
import com.cloudmart.community.mq.CommunityEventProducer;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.PostShareMapper;
import com.cloudmart.community.service.UserEnrichmentService;
import com.cloudmart.community.service.UserEnrichmentService.UserInfo;
import com.cloudmart.community.vo.PostShareVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostShareServiceImplTest {

    @Mock
    private PostShareMapper postShareMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private UserEnrichmentService userEnrichmentService;

    @Mock
    private CommunityEventProducer communityEventProducer;

    private PostShareServiceImpl postShareService;

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long POST_ID = 100L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant1 = new MapperBuilderAssistant(configuration, "");
        assistant1.setCurrentNamespace("com.cloudmart.community.repository.PostShareMapper");
        TableInfoHelper.initTableInfo(assistant1, PostShare.class);
        MapperBuilderAssistant assistant2 = new MapperBuilderAssistant(configuration, "");
        assistant2.setCurrentNamespace("com.cloudmart.community.repository.PostMapper");
        TableInfoHelper.initTableInfo(assistant2, Post.class);
    }

    @BeforeEach
    void setUp() {
        postShareService = new PostShareServiceImpl(
                postShareMapper, postMapper, userEnrichmentService, communityEventProducer
        );
    }

    private Post buildPost(Long userId) {
        Post post = new Post();
        post.setId(POST_ID);
        post.setUserId(userId);
        post.setTitle("Test Post");
        post.setContent("Test content");
        post.setShareCount(5);
        return post;
    }

    private PostShare buildPostShare() {
        PostShare share = new PostShare();
        share.setId(1L);
        share.setPostId(POST_ID);
        share.setUserId(USER_ID);
        share.setChannel("LINK");
        return share;
    }

    @Nested
    @DisplayName("sharePost")
    class SharePostTests {

        @Test
        @DisplayName("should share post and increment share count")
        void sharePost_success() {
            Post post = buildPost(OTHER_USER_ID);
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postShareMapper.insert(any(PostShare.class))).thenAnswer(invocation -> {
                PostShare share = invocation.getArgument(0);
                share.setId(1L);
                return 1;
            });
            when(userEnrichmentService.getSingleUser(USER_ID))
                    .thenReturn(new UserInfo(USER_ID, "testUser", "avatar.png", null, null));

            PostShareVO result = postShareService.sharePost(USER_ID, POST_ID, "WECHAT");

            assertThat(result).isNotNull();
            assertThat(result.postId()).isEqualTo(POST_ID);
            assertThat(result.userId()).isEqualTo(USER_ID);
            assertThat(result.channel()).isEqualTo("WECHAT");
            assertThat(result.userNickname()).isEqualTo("testUser");
            assertThat(post.getShareCount()).isEqualTo(6);
            verify(postShareMapper).insert(any(PostShare.class));
            verify(postMapper).updateById(post);
            verify(communityEventProducer).publishShareEvent(OTHER_USER_ID, USER_ID, POST_ID, "Test Post", "WECHAT");
        }

        @Test
        @DisplayName("should default channel to LINK when channel is null")
        void sharePost_nullChannel_defaultsToLink() {
            Post post = buildPost(OTHER_USER_ID);
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postShareMapper.insert(any(PostShare.class))).thenAnswer(invocation -> {
                PostShare share = invocation.getArgument(0);
                share.setId(1L);
                return 1;
            });
            when(userEnrichmentService.getSingleUser(USER_ID))
                    .thenReturn(new UserInfo(USER_ID, "testUser", "avatar.png", null, null));

            PostShareVO result = postShareService.sharePost(USER_ID, POST_ID, null);

            assertThat(result.channel()).isEqualTo("LINK");
        }

        @Test
        @DisplayName("should not publish share event when sharing own post")
        void sharePost_ownPost_noEvent() {
            Post post = buildPost(USER_ID);
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postShareMapper.insert(any(PostShare.class))).thenAnswer(invocation -> {
                PostShare share = invocation.getArgument(0);
                share.setId(1L);
                return 1;
            });
            when(userEnrichmentService.getSingleUser(USER_ID))
                    .thenReturn(new UserInfo(USER_ID, "testUser", "avatar.png", null, null));

            postShareService.sharePost(USER_ID, POST_ID, "LINK");

            verify(communityEventProducer, never()).publishShareEvent(anyLong(), anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("should set share count to 1 when it is null")
        void sharePost_nullShareCount_setsToOne() {
            Post post = buildPost(OTHER_USER_ID);
            post.setShareCount(null);
            when(postMapper.selectById(POST_ID)).thenReturn(post);
            when(postShareMapper.insert(any(PostShare.class))).thenAnswer(invocation -> {
                PostShare share = invocation.getArgument(0);
                share.setId(1L);
                return 1;
            });
            when(userEnrichmentService.getSingleUser(USER_ID))
                    .thenReturn(new UserInfo(USER_ID, "testUser", "avatar.png", null, null));

            postShareService.sharePost(USER_ID, POST_ID, "LINK");

            assertThat(post.getShareCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw when post not found")
        void sharePost_postNotFound_throwsException() {
            when(postMapper.selectById(POST_ID)).thenReturn(null);

            assertThatThrownBy(() -> postShareService.sharePost(USER_ID, POST_ID, "LINK"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("POST_NOT_FOUND");
                    });

            verify(postShareMapper, never()).insert(any(PostShare.class));
        }
    }

    @Nested
    @DisplayName("getPostShares")
    class GetPostSharesTests {

        @Test
        @DisplayName("should return empty list when no shares found")
        void getPostShares_emptyResult() {
            when(postShareMapper.selectList(any())).thenReturn(List.of());

            List<PostShareVO> result = postShareService.getPostShares(POST_ID, 1, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should return share list with user info")
        void getPostShares_success() {
            PostShare share = buildPostShare();
            when(postShareMapper.selectList(any())).thenReturn(List.of(share));
            when(userEnrichmentService.batchGetUsers(any(HashSet.class)))
                    .thenReturn(Map.of(USER_ID, new UserInfo(USER_ID, "testUser", "avatar.png", null, null)));

            List<PostShareVO> result = postShareService.getPostShares(POST_ID, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).postId()).isEqualTo(POST_ID);
            assertThat(result.get(0).userNickname()).isEqualTo("testUser");
        }

        @Test
        @DisplayName("should use fallback user info when user not in map")
        void getPostShares_missingUser_fallbackInfo() {
            PostShare share = buildPostShare();
            when(postShareMapper.selectList(any())).thenReturn(List.of(share));
            when(userEnrichmentService.batchGetUsers(any(HashSet.class)))
                    .thenReturn(Map.of());

            List<PostShareVO> result = postShareService.getPostShares(POST_ID, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).userNickname()).isEqualTo("未知用户");
        }

        @Test
        @DisplayName("should clamp page and size to safe bounds")
        void getPostShares_clampBounds() {
            when(postShareMapper.selectList(any())).thenReturn(List.of());

            postShareService.getPostShares(POST_ID, -1, 0);

            verify(postShareMapper).selectList(any());
        }
    }
}
