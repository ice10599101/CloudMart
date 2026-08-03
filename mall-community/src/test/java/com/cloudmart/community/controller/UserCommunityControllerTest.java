package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.service.UserCommunityService;
import com.cloudmart.community.service.UserFollowService;
import com.cloudmart.community.vo.PostVO;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserCommunityControllerTest {

    private MockMvc mockMvc;

    private final UserCommunityService userCommunityService = Mockito.mock(UserCommunityService.class);
    private final UserFollowService userFollowService = Mockito.mock(UserFollowService.class);
    private final PostService postService = Mockito.mock(PostService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserCommunityController(userCommunityService, userFollowService, postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private UserCommunityVO buildUserCommunityVO() {
        return new UserCommunityVO(
                2L, "目标用户", "https://avatar.example.com/2.png", "个性签名",
                10L, 5L, 20L, 3L, List.of(), true);
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
}
