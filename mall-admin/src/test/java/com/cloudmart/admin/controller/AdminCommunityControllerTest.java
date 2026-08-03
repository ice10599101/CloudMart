package com.cloudmart.admin.controller;

import com.cloudmart.admin.feign.CommunityFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCommunityControllerTest {

    private MockMvc mockMvc;
    private CommunityFeignClient communityFeignClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        communityFeignClient = mock(CommunityFeignClient.class);
        AdminCommunityController controller = new AdminCommunityController(communityFeignClient);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("GET /stats/overview - 社区概览统计")
    class GetStatsOverviewTests {

        @Test
        @DisplayName("返回社区概览统计数据")
        void getStatsOverview_returnsOverviewStats() throws Exception {
            Map<String, Object> overview = Map.of(
                    "totalPosts", 1000, "totalComments", 5000, "totalUsers", 200
            );
            given(communityFeignClient.getStatsOverview()).willReturn(ApiResponse.ok(overview));

            mockMvc.perform(get("/stats/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalPosts").value(1000));

            verify(communityFeignClient).getStatsOverview();
        }
    }

    @Nested
    @DisplayName("GET /community/posts - 帖子列表")
    class ListPostsTests {

        @Test
        @DisplayName("返回社区帖子列表")
        void listPosts_returnsPostList() throws Exception {
            ApiResponse<Object> apiResponse = ApiResponse.ok(List.of(Map.of("id", 1, "title", "Test Post")));
            given(communityFeignClient.listPosts(any())).willReturn(apiResponse);

            mockMvc.perform(get("/community/posts")
                            .param("page", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(communityFeignClient).listPosts(any());
        }
    }

    @Nested
    @DisplayName("PUT /community/posts/{id}/status - 更新帖子状态")
    class UpdatePostStatusTests {

        @Test
        @DisplayName("更新帖子状态成功")
        void updatePostStatus_statusUpdatedSuccessfully() throws Exception {
            given(communityFeignClient.updatePostStatus(anyLong(), any())).willReturn(ApiResponse.ok(null));

            mockMvc.perform(put("/community/posts/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(communityFeignClient).updatePostStatus(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /community/tags/{id} - 删除标签")
    class DeleteTagTests {

        @Test
        @DisplayName("删除标签成功")
        void deleteTag_tagDeletedSuccessfully() throws Exception {
            given(communityFeignClient.deleteTag(1L)).willReturn(ApiResponse.ok(null));

            mockMvc.perform(delete("/community/tags/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(communityFeignClient).deleteTag(1L);
        }
    }

    @Nested
    @DisplayName("PUT /community/reports/{id}/handle - 处理举报")
    class HandleReportTests {

        @Test
        @DisplayName("处理举报成功")
        void handleReport_reportHandledSuccessfully() throws Exception {
            given(communityFeignClient.handleReport(anyLong(), any())).willReturn(ApiResponse.ok(null));

            mockMvc.perform(put("/community/reports/1/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"result\":\"resolved\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(communityFeignClient).handleReport(anyLong(), any());
        }
    }
}
