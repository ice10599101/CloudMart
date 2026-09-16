package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.service.PrivacyService;
import com.cloudmart.community.service.UserCommunityService;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.CommentVO;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.PrivacyVisibility;
import com.cloudmart.community.vo.TagVO;
import com.cloudmart.community.vo.UserCommunityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserCommunityControllerTest {

    private MockMvc mockMvc;

    private final UserCommunityService userCommunityService = Mockito.mock(UserCommunityService.class);
    private final UserFollowService userFollowService = Mockito.mock(UserFollowService.class);
    private final PostService postService = Mockito.mock(PostService.class);
    private final PostCommentService postCommentService = Mockito.mock(PostCommentService.class);
    private final com.cloudmart.community.service.UserEnrichmentService userEnrichmentService = Mockito.mock(com.cloudmart.community.service.UserEnrichmentService.class);
    private final PrivacyService privacyService = Mockito.mock(PrivacyService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        given(privacyService.checkVisibility(any(), any()))
                .willReturn(new PrivacyVisibility(true, true, true, true, true, true));
        mockMvc = MockMvcBuilders.standaloneSetup(new UserCommunityController(userCommunityService, userFollowService, postService, postCommentService, userEnrichmentService, privacyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserCommunityVO buildUserCommunityVO() {
        return new UserCommunityVO(
                2L, "目标用户", "https://avatar.example.com/2.png", "个性签名",
                10L, 5L, 20L, 3L, null, true, false);
    }

    @Test
    @DisplayName("GET /users/{userId}/profile - 获取用户社区资料成功")
    void getUserProfile_ShouldReturnSuccess() throws Exception {
        UserCommunityVO vo = buildUserCommunityVO();
        given(userCommunityService.getUserProfile(2L, 1L)).willReturn(vo);

        mockMvc.perform(get("/users/2/profile")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(2))
                .andExpect(jsonPath("$.data.nickname").value("目标用户"))
                .andExpect(jsonPath("$.data.isFollowed").value(true));
    }

    @Test
    @DisplayName("GET /users/{userId}/profile - 无登录用户也能获取资料")
    void getUserProfile_WithoutUserId_ShouldReturnSuccess() throws Exception {
        UserCommunityVO vo = buildUserCommunityVO();
        given(userCommunityService.getUserProfile(2L, null)).willReturn(vo);

        mockMvc.perform(get("/users/2/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(2));
    }

    @Test
    @DisplayName("POST /users/{userId}/follow - 关注用户成功")
    void follow_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(userFollowService).follow(1L, 2L);

        mockMvc.perform(post("/users/2/follow")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /users/{userId}/follow - 缺少X-User-Id头返回401")
    void follow_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/users/2/follow"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /users/{userId}/follow - 取消关注成功")
    void unfollow_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(userFollowService).unfollow(1L, 2L);

        mockMvc.perform(delete("/users/2/follow")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /users/{userId}/collections - 获取用户收藏列表成功")
    void getUserCollections_ShouldReturnSuccess() throws Exception {
        PostVO postVO = new PostVO(
                1L, 2L, "目标用户", "https://avatar.example.com/2.png",
                "收藏的帖子", "内容", null, List.of(), "IMAGE", null, null,
                10, 5, 3, 1, 100, 1, 1, null, false,
                List.of(new TagVO(1L, "技术", null, 5, true, 1, LocalDateTime.now())),
                false, true, LocalDateTime.now(), LocalDateTime.now());
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(postVO));
        given(postService.getUserCollections(2L, 1, 20, 1L)).willReturn(page);

        mockMvc.perform(get("/users/2/collections")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /users/{userId}/followers - 获取粉丝列表成功")
    void getFollowers_ShouldReturnSuccess() throws Exception {
        UserCommunityVO vo = buildUserCommunityVO();
        given(userFollowService.getFollowerList(2L, 1L, 1, 20)).willReturn(List.of(vo));

        mockMvc.perform(get("/users/2/followers")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userId").value(2));
    }

    @Test
    @DisplayName("GET /users/{userId}/following - 获取关注列表成功")
    void getFollowingList_ShouldReturnSuccess() throws Exception {
        UserCommunityVO vo = buildUserCommunityVO();
        given(userFollowService.getFollowingList(2L, 1L, 1, 20)).willReturn(List.of(vo));

        mockMvc.perform(get("/users/2/following")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userId").value(2));
    }

    @Test
    @DisplayName("GET /users/recommend - 获取推荐用户成功")
    void getRecommendedUsers_ShouldReturnSuccess() throws Exception {
        UserCommunityVO vo = buildUserCommunityVO();
        given(userFollowService.getRecommendedUsers(1L, 6)).willReturn(List.of(vo));

        mockMvc.perform(get("/users/recommend")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userId").value(2));
    }

    @Test
    @DisplayName("GET /users/recommend - 无登录用户也能获取推荐")
    void getRecommendedUsers_WithoutUserId_ShouldReturnSuccess() throws Exception {
        UserCommunityVO vo = buildUserCommunityVO();
        given(userFollowService.getRecommendedUsers(null, 6)).willReturn(List.of(vo));

        mockMvc.perform(get("/users/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /users/{userId}/comments - TA 的评论返回信封与分页 meta")
    void getUserComments_ShouldReturnSuccessEnvelope() throws Exception {
        CommentVO comment = new CommentVO(9L, 3L, "帖子标题", "<p>评论内容</p>",
                null, null, 2, 1, LocalDateTime.now());
        Page<CommentVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(comment));
        given(postCommentService.getMyComments(2L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/users/2/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].postTitle").value("帖子标题"));
    }

    @Test
    @DisplayName("GET /users/{userId}/privacy - 返回当前查看者可见性")
    void getPrivacyStatus_ShouldReturnVisibility() throws Exception {
        given(privacyService.checkVisibility(1L, 2L))
                .willReturn(new PrivacyVisibility(false, false, true, true, true, false));

        mockMvc.perform(get("/users/2/privacy")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.birthdayVisible").value(false))
                .andExpect(jsonPath("$.data.followersVisible").value(true))
                .andExpect(jsonPath("$.data.postsVisible").value(false));
    }

    @Test
    @DisplayName("GET /users/{userId}/privacy - 匿名查看返回陌生人档位可见性")
    void getPrivacyStatus_WithoutUserId_ShouldReturnVisibility() throws Exception {
        given(privacyService.checkVisibility(null, 2L))
                .willReturn(new PrivacyVisibility(false, false, false, false, false, false));

        mockMvc.perform(get("/users/2/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.followersVisible").value(false));
    }

    @Test
    @DisplayName("GET /users/{userId}/liked - 本人查看返回点赞列表")
    void getUserLikedPosts_SelfView_ShouldReturnSuccessEnvelope() throws Exception {
        PostVO post = Mockito.mock(PostVO.class);
        Page<PostVO> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(post));
        given(postService.getLikedPosts(2L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/users/2/liked")
                        .header(USER_ID_HEADER, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /users/{userId}/liked - 他人查看返回空列表（点赞默认仅自己可见）")
    void getUserLikedPosts_OtherView_ShouldReturnEmpty() throws Exception {
        mockMvc.perform(get("/users/2/liked")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(0));
        verify(postService, never()).getLikedPosts(anyLong(), anyInt(), anyInt());
    }
}
