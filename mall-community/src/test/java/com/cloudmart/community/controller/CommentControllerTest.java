package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.vo.CommentVO;
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

class CommentControllerTest {

    private MockMvc mockMvc;

    private final PostCommentService postCommentService = Mockito.mock(PostCommentService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommentController(postCommentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CommentVO buildCommentVO() {
        return new CommentVO(
                1L, 100L, "测试帖子标题", "测试评论内容",
                null, null, 3, 1, LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /comments/mine - 获取我的评论列表成功")
    void getMyComments_ShouldReturnSuccess() throws Exception {
        CommentVO vo = buildCommentVO();
        Page<CommentVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postCommentService.getMyComments(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/comments/mine")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].content").value("测试评论内容"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /comments/mine - 缺少X-User-Id头返回401")
    void getMyComments_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/comments/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /comments/{commentId}/like - 点赞评论成功")
    void likeComment_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postCommentService).likeComment(1L, 1L);

        mockMvc.perform(post("/comments/1/like")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /comments/{commentId}/like - 缺少X-User-Id头返回401")
    void likeComment_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/comments/1/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("DELETE /comments/{commentId}/like - 取消点赞评论成功")
    void unlikeComment_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postCommentService).unlikeComment(1L, 1L);

        mockMvc.perform(delete("/comments/1/like")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /comments/{commentId}/like - 缺少X-User-Id头返回401")
    void unlikeComment_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(delete("/comments/1/like"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
