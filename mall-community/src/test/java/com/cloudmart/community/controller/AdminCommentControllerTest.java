package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.PostCommentService;
import com.cloudmart.community.vo.PostCommentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCommentControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PostCommentService postCommentService = Mockito.mock(PostCommentService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCommentController(postCommentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PostCommentVO buildCommentVO() {
        return new PostCommentVO(
                1L, 1L, 1L, "测试用户", "https://avatar.example.com/1.png",
                null, null, null, "测试评论", 5, 1, false,
                List.of(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /admin/comments - 评论列表")
    class AdminListComments {

        @Test
        @DisplayName("分页查询评论列表成功")
        void shouldReturnPagedComments() throws Exception {
            PostCommentVO vo = buildCommentVO();
            Page<PostCommentVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(postCommentService.adminListComments(null, null, 1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/comments"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }

        @Test
        @DisplayName("带关键词和状态筛选查询成功")
        void shouldReturnFilteredComments() throws Exception {
            PostCommentVO vo = buildCommentVO();
            Page<PostCommentVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(postCommentService.adminListComments("测试", 1, 1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/comments")
                            .param("keyword", "测试")
                            .param("status", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("PUT /admin/comments/{id}/status - 更新评论状态")
    class AdminUpdateCommentStatus {

        @Test
        @DisplayName("更新评论状态成功")
        void shouldUpdateCommentStatus() throws Exception {
            willDoNothing().given(postCommentService).adminUpdateCommentStatus(1L, 0);

            mockMvc.perform(put("/admin/comments/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", 0))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
