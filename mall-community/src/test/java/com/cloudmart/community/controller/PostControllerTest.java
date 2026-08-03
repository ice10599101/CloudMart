package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.CreatePostRequest;
import com.cloudmart.community.dto.UpdatePostRequest;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.service.PostShareService;
import com.cloudmart.community.service.SearchService;
import com.cloudmart.community.vo.PostShareVO;
import com.cloudmart.community.vo.PostVO;
import com.cloudmart.community.vo.TagVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PostService postService = Mockito.mock(PostService.class);
    private final PostShareService postShareService = Mockito.mock(PostShareService.class);
    private final SearchService searchService = Mockito.mock(SearchService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PostController(postService, postShareService, searchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PostVO buildPostVO() {
        return new PostVO(
                1L, 1L, "测试用户", "https://avatar.example.com/1.png",
                "测试标题", "测试内容", "https://cover.example.com/1.png",
                List.of(), "IMAGE", null, null,
                10, 5, 3, 1, 100, 1, 1, null, false,
                List.of(new TagVO(1L, "技术", null, 5, true, 1, LocalDateTime.now())),
                true, false, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /posts - 发布帖子成功")
    void createPost_ShouldReturnSuccess() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "测试标题", "测试内容", null, null, null, null, null, List.of(1L), 1);
        PostVO vo = buildPostVO();
        given(postService.createPost(eq(1L), any(CreatePostRequest.class))).willReturn(vo);

        mockMvc.perform(post("/posts")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试标题"))
                .andExpect(jsonPath("$.data.authorNickname").value("测试用户"));
    }

    @Test
    @DisplayName("POST /posts - 缺少X-User-Id头返回401")
    void createPost_WithoutUserId_ShouldReturn401() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "测试标题", "测试内容", null, null, null, null, null, List.of(1L), 1);

        mockMvc.perform(post("/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /posts - 标题为空返回校验失败")
    void createPost_WithBlankTitle_ShouldReturnValidationError() throws Exception {
        CreatePostRequest request = new CreatePostRequest(
                "", "测试内容", null, null, null, null, null, List.of(1L), 1);

        mockMvc.perform(post("/posts")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /posts/{id} - 更新帖子成功")
    void updatePost_ShouldReturnSuccess() throws Exception {
        UpdatePostRequest request = new UpdatePostRequest(
                "更新标题", "更新内容", null, null, null, null, null, null, null);
        PostVO vo = buildPostVO();
        given(postService.updatePost(eq(1L), eq(1L), any(UpdatePostRequest.class))).willReturn(vo);

        mockMvc.perform(put("/posts/1")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("DELETE /posts/{id} - 删除帖子成功")
    void deletePost_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postService).deletePost(1L, 1L);

        mockMvc.perform(delete("/posts/1")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /posts/{id} - 获取帖子详情成功")
    void getPostDetail_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        given(postService.getPostDetail(1L, 1L)).willReturn(vo);

        mockMvc.perform(get("/posts/1")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("测试标题"));
    }

    @Test
    @DisplayName("GET /posts/{id} - 无登录用户也能获取帖子详情")
    void getPostDetail_WithoutUserId_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        given(postService.getPostDetail(1L, null)).willReturn(vo);

        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("GET /posts/feed - 获取信息流成功")
    void getFeedPosts_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postService.getFeedPosts("recommend", 1, 20, 1L)).willReturn(page);

        mockMvc.perform(get("/posts/feed")
                        .param("tab", "recommend")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /posts/feed/following - 获取关注动态成功")
    void getFollowingFeed_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postService.getFollowingFeed(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/posts/feed/following")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /posts/feed/following - 未登录返回空列表")
    void getFollowingFeed_WithoutUserId_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/posts/feed/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    @DisplayName("GET /posts/search - 搜索帖子成功")
    void searchPosts_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postService.searchPosts("测试", 1, 20, 1L)).willReturn(page);
        willDoNothing().given(searchService).recordSearch(1L, "测试");

        mockMvc.perform(get("/posts/search")
                        .param("keyword", "测试")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /posts/users/{userId} - 获取用户帖子列表成功")
    void getUserPosts_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postService.getUserPosts(2L, 1, 20, 1L)).willReturn(page);

        mockMvc.perform(get("/posts/users/2")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /posts/drafts - 获取草稿列表成功")
    void getUserDrafts_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 0L);
        page.setRecords(List.of(vo));
        given(postService.getUserDrafts(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/posts/drafts")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("GET /posts/liked - 获取点赞帖子列表成功")
    void getLikedPosts_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postService.getLikedPosts(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/posts/liked")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].isLiked").value(true));
    }

    @Test
    @DisplayName("GET /posts/tags/{tagId} - 获取标签帖子列表成功")
    void getPostsByTag_ShouldReturnSuccess() throws Exception {
        PostVO vo = buildPostVO();
        Page<PostVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postService.getPostsByTag(1L, 1, 20, 1L)).willReturn(page);

        mockMvc.perform(get("/posts/tags/1")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /posts/{id}/like - 点赞帖子成功")
    void likePost_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postService).likePost(1L, 1L);

        mockMvc.perform(post("/posts/1/like")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /posts/{id}/like - 取消点赞成功")
    void unlikePost_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postService).unlikePost(1L, 1L);

        mockMvc.perform(delete("/posts/1/like")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /posts/{id}/collect - 收藏帖子成功")
    void collectPost_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postService).collectPost(1L, 1L);

        mockMvc.perform(post("/posts/1/collect")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /posts/{id}/collect - 取消收藏成功")
    void uncollectPost_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postService).uncollectPost(1L, 1L);

        mockMvc.perform(delete("/posts/1/collect")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /posts/{id}/share - 分享帖子成功")
    void sharePost_ShouldReturnSuccess() throws Exception {
        PostShareVO shareVO = new PostShareVO(
                1L, 1L, 1L, "测试用户", "https://avatar.example.com/1.png",
                "LINK", LocalDateTime.now());
        given(postShareService.sharePost(1L, 1L, "LINK")).willReturn(shareVO);

        mockMvc.perform(post("/posts/1/share")
                        .header(USER_ID_HEADER, 1)
                        .param("channel", "LINK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.channel").value("LINK"));
    }

    @Test
    @DisplayName("GET /posts/{id}/shares - 获取帖子分享记录成功")
    void getPostShares_ShouldReturnSuccess() throws Exception {
        PostShareVO shareVO = new PostShareVO(
                1L, 1L, 1L, "测试用户", "https://avatar.example.com/1.png",
                "LINK", LocalDateTime.now());
        given(postShareService.getPostShares(1L, 1, 20)).willReturn(List.of(shareVO));

        mockMvc.perform(get("/posts/1/shares"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].channel").value("LINK"));
    }
}
