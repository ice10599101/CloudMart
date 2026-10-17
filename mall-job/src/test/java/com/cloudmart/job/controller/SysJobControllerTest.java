package com.cloudmart.job.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.job.service.impl.XxlJobAdapterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OPS-01：适配层契约——只读 XXL 事实（list/详情/日志）+ 白名单手动运行；
 * 本地调度的写接口（新建/编辑/启停/删除）已退役。
 */
class SysJobControllerTest {

    private XxlJobAdapterService adapterService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        adapterService = Mockito.mock(XxlJobAdapterService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SysJobController(adapterService))
                .setControllerAdvice(new com.cloudmart.common.handler.GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /job/list 返回 XXL 事实分页")
    void list_ok() throws Exception {
        given(adapterService.page(anyInt(), anyInt(), any()))
                .willReturn(Map.of("records", List.of(Map.of("id", 1, "executorHandler", "orderTimeoutCancelHandler")),
                        "total", 1));
        mockMvc.perform(get("/job/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].executorHandler").value("orderTimeoutCancelHandler"));
    }

    @Test
    @DisplayName("POST /job/{id}/run 白名单手动运行")
    void run_ok() throws Exception {
        mockMvc.perform(post("/job/1/run").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        verify(adapterService).runOnce(1L);
    }

    @Test
    @DisplayName("runOnce 空 handler 任务拒绝（JOB_HANDLER_EMPTY，400 业务信封）")
    void run_empty_handler_rejected() throws Exception {
        doThrow(new BusinessException("JOB_HANDLER_EMPTY", "任务未配置 executor_handler"))
                .when(adapterService).runOnce(19L);
        mockMvc.perform(post("/job/19/run").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("JOB_HANDLER_EMPTY"));
    }

    @Test
    @DisplayName("本地调度写接口已退役（POST /job 不再映射业务写入）")
    void create_retired() throws Exception {
        int status = mockMvc.perform(post("/job").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isGreaterThanOrEqualTo(400);
    }
}
