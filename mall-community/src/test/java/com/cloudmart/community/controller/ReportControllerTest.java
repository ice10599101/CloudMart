package com.cloudmart.community.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.community.dto.CreateReportRequest;
import com.cloudmart.community.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReportControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReportService reportService = Mockito.mock(ReportService.class);

    private static final String USER_ID_HEADER = "X-User-Id";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReportController(reportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /reports - 提交举报成功")
    void createReport_ShouldReturnSuccess() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "POST", 1L, "垃圾广告", "该帖子包含垃圾广告内容", List.of());
        willDoNothing().given(reportService).createReport(eq(1L), any(CreateReportRequest.class));

        mockMvc.perform(post("/reports")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /reports - 缺少X-User-Id头返回401")
    void createReport_WithoutUserId_ShouldReturn401() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "POST", 1L, "垃圾广告", "该帖子包含垃圾广告内容", List.of());

        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /reports - targetType为空返回校验失败")
    void createReport_WithBlankTargetType_ShouldReturnValidationError() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "", 1L, "垃圾广告", null, null);

        mockMvc.perform(post("/reports")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /reports - targetId为空返回校验失败")
    void createReport_WithNullTargetId_ShouldReturnValidationError() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "POST", null, "垃圾广告", null, null);

        mockMvc.perform(post("/reports")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /reports - reason为空返回校验失败")
    void createReport_WithBlankReason_ShouldReturnValidationError() throws Exception {
        CreateReportRequest request = new CreateReportRequest(
                "POST", 1L, "", null, null);

        mockMvc.perform(post("/reports")
                        .header(USER_ID_HEADER, 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
