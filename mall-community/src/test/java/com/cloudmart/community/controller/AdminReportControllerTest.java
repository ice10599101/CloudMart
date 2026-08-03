package com.cloudmart.community.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.HandleReportRequest;
import com.cloudmart.community.service.ReportService;
import com.cloudmart.community.vo.ReportVO;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportService reportService = Mockito.mock(ReportService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ReportVO buildReportVO() {
        return new ReportVO(
                1L, 1L, "举报人", "POST", 10L, "垃圾广告", "描述信息",
                List.of(), 0, null, null, null, null, LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /admin/reports - 举报列表")
    class AdminListReports {

        @Test
        @DisplayName("分页查询举报列表成功")
        void shouldReturnPagedReports() throws Exception {
            ReportVO vo = buildReportVO();
            Page<ReportVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(reportService.adminListReports(null, null, 1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/reports"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.meta.page").value(1))
                    .andExpect(jsonPath("$.meta.total").value(1));
        }

        @Test
        @DisplayName("带状态和目标类型筛选查询成功")
        void shouldReturnFilteredReports() throws Exception {
            ReportVO vo = buildReportVO();
            Page<ReportVO> page = new Page<>(1, 20, 1L);
            page.setRecords(List.of(vo));
            given(reportService.adminListReports(0, "POST", 1, 20)).willReturn(page);

            mockMvc.perform(get("/admin/reports")
                            .param("status", "0")
                            .param("targetType", "POST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("PUT /admin/reports/{id}/handle - 处理举报")
    class HandleReport {

        @Test
        @DisplayName("处理举报成功")
        void shouldHandleReport() throws Exception {
            HandleReportRequest request = new HandleReportRequest(1, "已处理");
            willDoNothing().given(reportService).handleReport(eq(1L), eq(1L), any(HandleReportRequest.class));

            mockMvc.perform(put("/admin/reports/1/handle")
                            .header(USER_ID_HEADER, 1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("缺少X-User-Id头返回401")
        void shouldReturn401WithoutUserId() throws Exception {
            HandleReportRequest request = new HandleReportRequest(1, "已处理");

            mockMvc.perform(put("/admin/reports/1/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        }
    }
}
