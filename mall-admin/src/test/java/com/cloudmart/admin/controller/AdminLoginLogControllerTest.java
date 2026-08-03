package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminLoginLogQueryRequest;
import com.cloudmart.admin.dto.AdminLoginLogResponse;
import com.cloudmart.admin.dto.LoginLogRecordRequest;
import com.cloudmart.admin.service.AdminLoginLogService;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminLoginLogControllerTest {

    private MockMvc mockMvc;
    private AdminLoginLogService adminLoginLogService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        adminLoginLogService = mock(AdminLoginLogService.class);
        AdminLoginLogController controller = new AdminLoginLogController(adminLoginLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("GET /logs/login/page - 分页查询登录日志")
    class PageTests {

        @Test
        @DisplayName("返回分页登录日志列表")
        void page_returnsPagedLoginLogs() throws Exception {
            AdminLoginLogResponse logResponse = new AdminLoginLogResponse(
                    1L, "admin", "127.0.0.1", "内网", "Chrome", "Windows",
                    0, "登录成功", LocalDateTime.now());
            Page<AdminLoginLogResponse> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(logResponse));
            given(adminLoginLogService.page(any(AdminLoginLogQueryRequest.class))).willReturn(page);

            mockMvc.perform(get("/logs/login/page")
                            .param("page", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.records[0].username").value("admin"))
                    .andExpect(jsonPath("$.meta.total").value(1));

            verify(adminLoginLogService).page(any(AdminLoginLogQueryRequest.class));
        }
    }

    @Nested
    @DisplayName("DELETE /logs/login/{id} - 删除登录日志")
    class DeleteTests {

        @Test
        @DisplayName("删除登录日志成功")
        void delete_loginLogDeletedSuccessfully() throws Exception {
            doNothing().when(adminLoginLogService).delete(1L);

            mockMvc.perform(delete("/logs/login/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminLoginLogService).delete(1L);
        }
    }

    @Nested
    @DisplayName("POST /logs/login/record - 记录登录日志")
    class RecordLoginTests {

        @Test
        @DisplayName("内部调用记录登录日志成功")
        void recordLogin_withInternalHeader_recordsLoginLog() throws Exception {
            doNothing().when(adminLoginLogService).recordLogin(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), any(), anyString());

            LoginLogRecordRequest request = new LoginLogRecordRequest(
                    "admin", "127.0.0.1", "内网", "Chrome", "Windows", 0, "登录成功");

            mockMvc.perform(post("/logs/login/record")
                            .header(SecurityConstants.INTERNAL_CALL_HEADER, "true")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminLoginLogService).recordLogin(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), any(), anyString());
        }

        @Test
        @DisplayName("缺少内部调用头返回403")
        void recordLogin_withoutInternalHeader_returnsForbidden() throws Exception {
            LoginLogRecordRequest request = new LoginLogRecordRequest(
                    "admin", "127.0.0.1", "内网", "Chrome", "Windows", 0, "登录成功");

            mockMvc.perform(post("/logs/login/record")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
    }

    @Nested
    @DisplayName("DELETE /logs/login/clean - 清空登录日志")
    class CleanTests {

        @Test
        @DisplayName("清空登录日志成功")
        void clean_allLoginLogsCleanedSuccessfully() throws Exception {
            doNothing().when(adminLoginLogService).clean();

            mockMvc.perform(delete("/logs/login/clean"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminLoginLogService).clean();
        }
    }
}
