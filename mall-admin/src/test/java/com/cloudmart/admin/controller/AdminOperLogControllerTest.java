package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminOperLogQueryRequest;
import com.cloudmart.admin.dto.AdminOperLogResponse;
import com.cloudmart.admin.service.AdminOperLogService;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminOperLogControllerTest {

    private MockMvc mockMvc;
    private AdminOperLogService adminOperLogService;

    @BeforeEach
    void setUp() {
        adminOperLogService = mock(AdminOperLogService.class);
        AdminOperLogController controller = new AdminOperLogController(adminOperLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /logs/oper/page - 分页查询操作日志")
    class PageTests {

        @Test
        @DisplayName("返回分页操作日志列表")
        void page_returnsPagedOperLogs() throws Exception {
            AdminOperLogResponse logResponse = new AdminOperLogResponse(
                    1L, "用户管理", 1, "UserController.list()", "GET",
                    1, 1L, "admin", "技术部", "/users/page", "127.0.0.1",
                    "内网", null, null, 0, null, LocalDateTime.now(), 50L);
            Page<AdminOperLogResponse> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(logResponse));
            given(adminOperLogService.page(any(AdminOperLogQueryRequest.class))).willReturn(page);

            mockMvc.perform(get("/logs/oper/page")
                            .param("page", "1")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.records[0].title").value("用户管理"))
                    .andExpect(jsonPath("$.meta.total").value(1));

            verify(adminOperLogService).page(any(AdminOperLogQueryRequest.class));
        }
    }

    @Nested
    @DisplayName("GET /logs/oper/{id} - 查询操作日志详情")
    class GetByIdTests {

        @Test
        @DisplayName("返回指定ID的操作日志详情")
        void getById_returnsOperLogDetail() throws Exception {
            AdminOperLogResponse logResponse = new AdminOperLogResponse(
                    1L, "用户管理", 1, "UserController.list()", "GET",
                    1, 1L, "admin", "技术部", "/users/page", "127.0.0.1",
                    "内网", null, null, 0, null, LocalDateTime.now(), 50L);
            given(adminOperLogService.getById(1L)).willReturn(logResponse);

            mockMvc.perform(get("/logs/oper/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("用户管理"));

            verify(adminOperLogService).getById(1L);
        }
    }

    @Nested
    @DisplayName("DELETE /logs/oper/{id} - 删除操作日志")
    class DeleteTests {

        @Test
        @DisplayName("删除操作日志成功")
        void delete_operLogDeletedSuccessfully() throws Exception {
            doNothing().when(adminOperLogService).delete(1L);

            mockMvc.perform(delete("/logs/oper/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminOperLogService).delete(1L);
        }
    }

    @Nested
    @DisplayName("DELETE /logs/oper/clean - 清空操作日志")
    class CleanTests {

        @Test
        @DisplayName("清空操作日志成功")
        void clean_allOperLogsCleanedSuccessfully() throws Exception {
            doNothing().when(adminOperLogService).clean();

            mockMvc.perform(delete("/logs/oper/clean"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(adminOperLogService).clean();
        }
    }
}
