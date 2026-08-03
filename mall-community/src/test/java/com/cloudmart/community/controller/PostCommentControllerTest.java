package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.CreateCommentRequest;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.vo.PostCommentVO;
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

class PostCommentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PostCommentService postCommentService = Mockito.mock(PostCommentService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PostCommentController(postCommentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PostCommentVO buildCommentVO() {
        return new PostCommentVO(
                1L, 100L, 1L, "测试用户", "https://avatar.example.com/1.png",
                null, null, null, "测试评论", 5, 1, true,
                List.of(), LocalDateTime.now());
    }

    @Test
    @DisplayName("POST /posts/{postId}/comments - 发表评论成功")
    void createComment_ShouldReturnSuccess() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(100L, null, null, "测试评论");
        PostCommentVO vo = buildCommentVO();
        given(postCommentService.createComment(eq(1L), eq(100L), any(CreateCommentRequest.class))).willReturn(vo);

        mockMvc.perform(post("/posts/100/comments")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.content").value("测试评论"));
    }

    @Test
    @DisplayName("POST /posts/{postId}/comments - 缺少X-User-Id头返回401")
    void createComment_WithoutUserId_ShouldReturn401() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(100L, null, null, "测试评论");

        mockMvc.perform(post("/posts/100/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /posts/{postId}/comments - 内容为空返回校验失败")
    void createComment_WithBlankContent_ShouldReturnValidationError() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest(100L, null, null, "");

        mockMvc.perform(post("/posts/100/comments")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /posts/{postId}/comments - 获取评论列表成功")
    void getComments_ShouldReturnSuccess() throws Exception {
        PostCommentVO vo = buildCommentVO();
        Page<PostCommentVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postCommentService.getComments(100L, 1, 20, 1L)).willReturn(page);

        mockMvc.perform(get("/posts/100/comments")
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
    @DisplayName("GET /posts/{postId}/comments - 无登录用户也能获取评论列表")
    void getComments_WithoutUserId_ShouldReturnSuccess() throws Exception {
        PostCommentVO vo = buildCommentVO();
        Page<PostCommentVO> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(vo));
        given(postCommentService.getComments(100L, 1, 20, null)).willReturn(page);

        mockMvc.perform(get("/posts/100/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("DELETE /posts/{postId}/comments/{commentId} - 删除评论成功")
    void deleteComment_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(postCommentService).deleteComment(1L, 1L);

        mockMvc.perform(delete("/posts/100/comments/1")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /posts/{postId}/comments/{commentId} - 缺少X-User-Id头返回401")
    void deleteComment_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(delete("/posts/100/comments/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
