package com.cloudmart.risk.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.risk.dto.RiskCheckRequest;
import com.cloudmart.risk.service.RiskCheckService;
import com.cloudmart.risk.vo.RiskCheckVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RiskCheckControllerTest {

    private MockMvc mockMvc;

    private final RiskCheckService riskCheckService = Mockito.mock(RiskCheckService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RiskCheckController(riskCheckService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("执行风控检查 - 通过返回信封")
    void check_WhenPassed_ShouldReturnEnvelope() throws Exception {
        RiskCheckVO vo = new RiskCheckVO(true, "LOW", null, null);

        given(riskCheckService.check(Mockito.any(RiskCheckRequest.class))).willReturn(vo);

        mockMvc.perform(post("/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"actionType\":\"ORDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"));
    }

    @Test
    @DisplayName("执行风控检查 - 未通过返回信封")
    void check_WhenBlocked_ShouldReturnEnvelope() throws Exception {
        RiskCheckVO vo = new RiskCheckVO(false, "HIGH", "频繁下单", "频率限制");

        given(riskCheckService.check(Mockito.any(RiskCheckRequest.class))).willReturn(vo);

        mockMvc.perform(post("/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"actionType\":\"ORDER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.reason").value("频繁下单"))
                .andExpect(jsonPath("$.data.ruleName").value("频率限制"));
    }

    @Test
    @DisplayName("执行风控检查 - 缺少必填字段返回校验错误")
    void check_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
