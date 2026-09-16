package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.service.BrowseHistoryService;
import com.cloudmart.community.vo.BrowseHistoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrowseHistoryControllerTest {

    private MockMvc mockMvc;

    private final BrowseHistoryService browseHistoryService = Mockito.mock(BrowseHistoryService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BrowseHistoryController(browseHistoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /browse-history - 正常上报成功")
    void record_ShouldReturnSuccess() throws Exception {
        willDoNothing().given(browseHistoryService)
                .recordBrowse(1L, "PRODUCT", 88L, "华为手机", "http://x/1.jpg");

        mockMvc.perform(post("/browse-history")
                        .header(USER_ID_HEADER, 1)
                        .contentType("application/json")
                        .content("{\"targetType\":\"PRODUCT\",\"targetId\":88,\"title\":\"华为手机\",\"cover\":\"http://x/1.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(browseHistoryService).recordBrowse(1L, "PRODUCT", 88L, "华为手机", "http://x/1.jpg");
    }

    @Test
    @DisplayName("POST /browse-history - 缺少X-User-Id头返回401")
    void record_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/browse-history")
                        .contentType("application/json")
                        .content("{\"targetType\":\"PRODUCT\",\"targetId\":88}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /browse-history - 非法 targetType 返回400")
    void record_WithInvalidTargetType_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/browse-history")
                        .header(USER_ID_HEADER, 1)
                        .contentType("application/json")
                        .content("{\"targetType\":\"REPLY\",\"targetId\":88}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /browse-history - 缺少 targetId 返回400")
    void record_WithoutTargetId_ShouldReturn400() throws Exception {
        mockMvc.perform(post("/browse-history")
                        .header(USER_ID_HEADER, 1)
                        .contentType("application/json")
                        .content("{\"targetType\":\"POST\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("GET /browse-history - 分页查询成功")
    void listMine_ShouldReturnPage() throws Exception {
        BrowseHistoryVO vo = new BrowseHistoryVO(
                5L, "WISH", 9L, "心愿标题", "http://x/w.jpg", LocalDateTime.of(2026, 9, 17, 10, 0));
        Page<BrowseHistoryVO> page = new Page<>(1, 20);
        page.setTotal(1L);
        page.setRecords(List.of(vo));
        given(browseHistoryService.listMyHistory(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/browse-history")
                        .header(USER_ID_HEADER, 1)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(5))
                .andExpect(jsonPath("$.data[0].targetType").value("WISH"))
                .andExpect(jsonPath("$.data[0].targetId").value(9))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /browse-history - 缺少X-User-Id头返回401")
    void listMine_WithoutUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/browse-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /browse-history - 使用默认分页参数")
    void listMine_WithDefaultPagination_ShouldUseDefaults() throws Exception {
        Page<BrowseHistoryVO> page = new Page<>(1, 20);
        page.setTotal(0L);
        page.setRecords(List.of());
        given(browseHistoryService.listMyHistory(1L, 1, 20)).willReturn(page);

        mockMvc.perform(get("/browse-history").header(USER_ID_HEADER, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.meta.total").value(0));

        verify(browseHistoryService).listMyHistory(eq(1L), eq(1), eq(20));
    }
}