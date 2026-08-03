package com.cloudmart.community.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchControllerTest {

    private MockMvc mockMvc;

    private final SearchService searchService = Mockito.mock(SearchService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /search/history - 获取搜索历史成功")
    void getSearchHistory_ShouldReturnSuccess() throws Exception {
        given(searchService.getUserSearchHistory(1L, 10)).willReturn(List.of("手机", "电脑"));

        mockMvc.perform(get("/search/history")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("手机"))
                .andExpect(jsonPath("$.data[1]").value("电脑"));
    }

    @Test
    @DisplayName("GET /search/history - 自定义limit参数成功")
    void getSearchHistory_WithCustomLimit_ShouldReturnSuccess() throws Exception {
        given(searchService.getUserSearchHistory(1L, 5)).willReturn(List.of("手机"));

        mockMvc.perform(get("/search/history")
                        .header(USER_ID_HEADER, 1)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("手机"));
    }

    @Test
    @DisplayName("GET /search/history - 未登录返回空列表")
    void getSearchHistory_WithoutUserId_ShouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/search/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("DELETE /search/history - 清空搜索历史成功")
    void clearSearchHistory_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(searchService).clearUserSearchHistory(1L);

        mockMvc.perform(delete("/search/history")
                        .header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /search/history - 缺少X-User-Id头返回401")
    void clearSearchHistory_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(delete("/search/history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /search/hot - 获取热搜词成功")
    void getHotSearches_ShouldReturnSuccess() throws Exception {
        given(searchService.getHotSearches(10)).willReturn(List.of("手机", "电脑", "耳机"));

        mockMvc.perform(get("/search/hot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("手机"))
                .andExpect(jsonPath("$.data[1]").value("电脑"))
                .andExpect(jsonPath("$.data[2]").value("耳机"));
    }

    @Test
    @DisplayName("GET /search/hot - 自定义limit参数成功")
    void getHotSearches_WithCustomLimit_ShouldReturnSuccess() throws Exception {
        given(searchService.getHotSearches(5)).willReturn(List.of("手机"));

        mockMvc.perform(get("/search/hot")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").value("手机"));
    }
}
