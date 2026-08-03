package com.cloudmart.community.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.CommunityStatsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityStatsControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CommunityStatsService communityStatsService = Mockito.mock(CommunityStatsService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommunityStatsController(communityStatsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /admin/stats/overview - 社区概览统计")
    class GetOverviewStats {

        @Test
        @DisplayName("获取社区概览统计成功")
        void shouldReturnOverviewStats() throws Exception {
            Map<String, Object> stats = Map.of(
                    "todayPosts", 10,
                    "pendingReview", 5,
                    "activeUsers", 100,
                    "totalPosts", 1000,
                    "pendingReports", 3
            );
            given(communityStatsService.getOverviewStats()).willReturn(stats);

            mockMvc.perform(get("/admin/stats/overview"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.todayPosts").value(10))
                    .andExpect(jsonPath("$.data.pendingReview").value(5))
                    .andExpect(jsonPath("$.data.activeUsers").value(100))
                    .andExpect(jsonPath("$.data.totalPosts").value(1000))
                    .andExpect(jsonPath("$.data.pendingReports").value(3));
        }
    }

    @Nested
    @DisplayName("GET /admin/stats/trend - 社区趋势统计")
    class GetTrendStats {

        @Test
        @DisplayName("获取默认7天趋势统计成功")
        void shouldReturnDefaultTrendStats() throws Exception {
            List<Map<String, Object>> trend = List.of(
                    Map.of("date", "2026-05-30", "posts", 10, "comments", 50, "reports", 2),
                    Map.of("date", "2026-05-31", "posts", 15, "comments", 60, "reports", 3)
            );
            given(communityStatsService.getTrendStats(7)).willReturn(trend);

            mockMvc.perform(get("/admin/stats/trend"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].date").value("2026-05-30"))
                    .andExpect(jsonPath("$.data[0].posts").value(10))
                    .andExpect(jsonPath("$.data[1].posts").value(15));
        }

        @Test
        @DisplayName("指定天数获取趋势统计成功")
        void shouldReturnTrendStatsWithCustomDays() throws Exception {
            List<Map<String, Object>> trend = List.of(
                    Map.of("date", "2026-05-31", "posts", 15, "comments", 60, "reports", 3)
            );
            given(communityStatsService.getTrendStats(1)).willReturn(trend);

            mockMvc.perform(get("/admin/stats/trend")
                            .param("days", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].posts").value(15));
        }
    }
}
