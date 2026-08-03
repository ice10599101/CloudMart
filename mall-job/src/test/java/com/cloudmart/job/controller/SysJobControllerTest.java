package com.cloudmart.job.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.job.dto.SysJobLogResponse;
import com.cloudmart.job.dto.SysJobRequest;
import com.cloudmart.job.dto.SysJobResponse;
import com.cloudmart.job.service.SysJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SysJobControllerTest {

    private MockMvc mockMvc;

    private final SysJobService sysJobService = Mockito.mock(SysJobService.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SysJobController(sysJobService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("任务列表 - 成功返回分页信封")
    void list_ShouldReturnPagedEnvelope() throws Exception {
        SysJobResponse response = new SysJobResponse(1L, "清理日志", "DEFAULT",
                "cleanLog", "0 0 2 * * ?", 1, 0, 0, "每日清理", FIXED_TIME, FIXED_TIME);
        Page<SysJobResponse> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(response));

        given(sysJobService.page(1, 20, null, null)).willReturn(page);

        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].jobName").value("清理日志"))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("任务详情 - 成功返回信封")
    void getInfo_ShouldReturnEnvelope() throws Exception {
        SysJobResponse response = new SysJobResponse(1L, "清理日志", "DEFAULT",
                "cleanLog", "0 0 2 * * ?", 1, 0, 0, "每日清理", FIXED_TIME, FIXED_TIME);

        given(sysJobService.getById(1L)).willReturn(response);

        mockMvc.perform(get("/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.jobName").value("清理日志"));
    }

    @Test
    @DisplayName("新增任务 - 成功返回信封")
    void add_ShouldReturnEnvelope() throws Exception {
        given(sysJobService.create(Mockito.any(SysJobRequest.class))).willReturn(1L);

        mockMvc.perform(post("")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"清理日志\",\"jobGroup\":\"DEFAULT\",\"invokeTarget\":\"cleanLog\",\"cronExpression\":\"0 0 2 * * ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    @DisplayName("新增任务 - 缺少必填字段返回校验错误")
    void add_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("修改任务 - 成功返回信封")
    void edit_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(put("/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"清理日志-更新\",\"jobGroup\":\"DEFAULT\",\"invokeTarget\":\"cleanLog\",\"cronExpression\":\"0 0 3 * * ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sysJobService).update(Mockito.eq(1L), Mockito.any(SysJobRequest.class));
    }

    @Test
    @DisplayName("删除任务 - 成功返回信封")
    void remove_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sysJobService).delete(1L);
    }

    @Test
    @DisplayName("切换状态 - 成功返回信封")
    void changeStatus_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(put("/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sysJobService).changeStatus(1L, 1);
    }

    @Test
    @DisplayName("立即执行 - 成功返回信封")
    void run_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(put("/1/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sysJobService).runOnce(1L);
    }

    @Test
    @DisplayName("任务日志分页 - 成功返回分页信封")
    void pageJobLogs_ShouldReturnPagedEnvelope() throws Exception {
        SysJobLogResponse logResponse = new SysJobLogResponse(1L, 1L, "清理日志", "DEFAULT",
                "cleanLog", "0 0 2 * * ?", "执行成功", 1, null, FIXED_TIME, FIXED_TIME, "100ms");
        Page<SysJobLogResponse> page = new Page<>(1, 20, 1L);
        page.setRecords(List.of(logResponse));

        given(sysJobService.pageJobLogs(null, 1, 20)).willReturn(page);

        mockMvc.perform(get("/log/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.meta.page").value(1));
    }

    @Test
    @DisplayName("删除日志 - 成功返回信封")
    void deleteLog_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/log/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sysJobService).deleteJobLog(1L);
    }

    @Test
    @DisplayName("清空日志 - 成功返回信封")
    void cleanLog_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/log/clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(sysJobService).cleanJobLogs();
    }
}
