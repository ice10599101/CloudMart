package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.entity.SensitiveWord;
import com.cloudmart.community.service.ContentReviewService;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReviewControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContentReviewService contentReviewService = Mockito.mock(ContentReviewService.class);
    private final PostService postService = Mockito.mock(PostService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminReviewController(contentReviewService, postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private PostVO buildPostVO() {
        return new PostVO(
                1L, 1L, "测试用户", "https://avatar.example.com/1.png",
                "测试标题", "测试内容", "https://cover.example.com/1.png",
                List.of(), "IMAGE", null, null,
                10, 5, 3, 1, 100, 1, 0, null, false,
                List.of(), true, false, LocalDateTime.now(), LocalDateTime.now());
    }

    private SensitiveWord buildSensitiveWord() {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(1L);
        sw.setWord("违禁词");
        sw.setCategory("GENERAL");
        sw.setLevel(1);
        sw.setCreatedAt(LocalDateTime.now());
        return sw;
    }

    @Nested
    @DisplayName("GET /admin/review/pending/posts - 待审核帖子")
    class ListPendingPosts {

        @Test
        @DisplayName("分页查询待审核帖子成功")
        void shouldReturnPendingPosts() throws Exception {
            PostVO vo = buildPostVO();
            Page<PostVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(postService.listPendingReviewPosts(1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/review/pending/posts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }
    }

    @Nested
    @DisplayName("PUT /admin/review/posts/{id}/approve - 审核通过")
    class ApprovePost {

        @Test
        @DisplayName("审核通过帖子成功")
        void shouldApprovePost() throws Exception {
            willDoNothing().given(postService).approvePost(1L);

            mockMvc.perform(put("/admin/review/posts/1/approve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("PUT /admin/review/posts/{id}/reject - 审核拒绝")
    class RejectPost {

        @Test
        @DisplayName("审核拒绝帖子成功")
        void shouldRejectPost() throws Exception {
            willDoNothing().given(postService).rejectPost(1L, "内容违规");

            mockMvc.perform(put("/admin/review/posts/1/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("reason", "内容违规"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("GET /admin/review/sensitive-words - 敏感词列表")
    class ListSensitiveWords {

        @Test
        @DisplayName("查询敏感词列表成功")
        void shouldReturnSensitiveWords() throws Exception {
            SensitiveWord sw = buildSensitiveWord();
            given(contentReviewService.listSensitiveWords(null, 1, 20)).willReturn(List.of(sw));

            mockMvc.perform(get("/admin/review/sensitive-words"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].word").value("违禁词"));
        }
    }

    @Nested
    @DisplayName("POST /admin/review/sensitive-words - 添加敏感词")
    class AddSensitiveWord {

        @Test
        @DisplayName("添加敏感词成功")
        void shouldAddSensitiveWord() throws Exception {
            SensitiveWord sw = buildSensitiveWord();
            given(contentReviewService.addSensitiveWord("新敏感词", "GENERAL", 1)).willReturn(sw);

            mockMvc.perform(post("/admin/review/sensitive-words")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("word", "新敏感词", "category", "GENERAL", "level", 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.word").value("违禁词"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/review/sensitive-words/{id} - 删除敏感词")
    class RemoveSensitiveWord {

        @Test
        @DisplayName("删除敏感词成功")
        void shouldRemoveSensitiveWord() throws Exception {
            willDoNothing().given(contentReviewService).removeSensitiveWord(1L);

            mockMvc.perform(delete("/admin/review/sensitive-words/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
