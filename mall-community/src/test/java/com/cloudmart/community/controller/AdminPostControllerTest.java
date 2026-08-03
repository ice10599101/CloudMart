package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.PostService;
import com.cloudmart.community.vo.PostVO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPostControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PostService postService = Mockito.mock(PostService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminPostController(postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PostVO buildPostVO() {
        return new PostVO(
                1L, 1L, "测试用户", "https://avatar.example.com/1.png",
                "测试标题", "测试内容", "https://cover.example.com/1.png",
                List.of(), "IMAGE", null, null,
                10, 5, 3, 1, 100, 1, 1, null, false,
                List.of(), true, false, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /admin/posts - 帖子列表")
    class AdminListPosts {

        @Test
        @DisplayName("分页查询帖子列表成功")
        void shouldReturnPagedPosts() throws Exception {
            PostVO vo = buildPostVO();
            Page<PostVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(postService.adminListPosts(null, null, null, 1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.pageSize").value(20))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }

        @Test
        @DisplayName("带关键词和状态筛选查询成功")
        void shouldReturnFilteredPosts() throws Exception {
            PostVO vo = buildPostVO();
            Page<PostVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(postService.adminListPosts("测试", 1, 2L, 1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/posts")
                            .param("keyword", "测试")
                            .param("status", "1")
                            .param("userId", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("PUT /admin/posts/{id}/status - 更新帖子状态")
    class AdminUpdatePostStatus {

        @Test
        @DisplayName("更新帖子状态成功")
        void shouldUpdatePostStatus() throws Exception {
            willDoNothing().given(postService).adminUpdatePostStatus(1L, 0);

            mockMvc.perform(put("/admin/posts/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("status", 0))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("PUT /admin/posts/{id}/top - 切换置顶")
    class AdminToggleTop {

        @Test
        @DisplayName("切换帖子置顶状态成功")
        void shouldToggleTop() throws Exception {
            willDoNothing().given(postService).adminToggleTop(1L, true);

            mockMvc.perform(put("/admin/posts/1/top")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("isTop", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
