package com.cloudmart.risk.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.risk.dto.CreateRiskRuleRequest;
import com.cloudmart.risk.dto.UpdateRiskRuleRequest;
import com.cloudmart.risk.service.RiskRuleService;
import com.cloudmart.risk.vo.RiskRuleVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RiskRuleControllerTest {

    private MockMvc mockMvc;

    private final RiskRuleService riskRuleService = Mockito.mock(RiskRuleService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RiskRuleController(riskRuleService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("创建风控规则 - 成功返回信封")
    void createRule_ShouldReturnEnvelope() throws Exception {
        RiskRuleVO vo = new RiskRuleVO(1L, "频率限制", "FREQUENCY", 10, "BLOCK", true);

        given(riskRuleService.createRule(Mockito.any(CreateRiskRuleRequest.class))).willReturn(vo);

        mockMvc.perform(post("/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"频率限制\",\"actionType\":\"BLOCK\",\"riskLevel\":\"HIGH\",\"threshold\":10,\"timeWindowMinutes\":60}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("频率限制"));
    }

    @Test
    @DisplayName("创建风控规则 - 缺少必填字段返回校验错误")
    void createRule_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("查询风控规则列表 - 成功返回信封")
    void listRules_ShouldReturnEnvelope() throws Exception {
        RiskRuleVO vo = new RiskRuleVO(1L, "频率限制", "FREQUENCY", 10, "BLOCK", true);

        given(riskRuleService.listRules()).willReturn(List.of(vo));

        mockMvc.perform(get("/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("频率限制"));
    }

    @Test
    @DisplayName("查询风控规则详情 - 成功返回信封")
    void getRule_ShouldReturnEnvelope() throws Exception {
        RiskRuleVO vo = new RiskRuleVO(1L, "频率限制", "FREQUENCY", 10, "BLOCK", true);

        given(riskRuleService.getRule(1L)).willReturn(vo);

        mockMvc.perform(get("/rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.threshold").value(10));
    }

    @Test
    @DisplayName("更新风控规则 - 成功返回信封")
    void updateRule_ShouldReturnEnvelope() throws Exception {
        RiskRuleVO vo = new RiskRuleVO(1L, "频率限制-更新", "FREQUENCY", 20, "WARN", true);

        given(riskRuleService.updateRule(Mockito.eq(1L), Mockito.any(UpdateRiskRuleRequest.class))).willReturn(vo);

        mockMvc.perform(put("/rules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"频率限制-更新\",\"threshold\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("频率限制-更新"))
                .andExpect(jsonPath("$.data.threshold").value(20));
    }

    @Test
    @DisplayName("删除风控规则 - 成功返回信封")
    void deleteRule_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(delete("/rules/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(riskRuleService).deleteRule(1L);
    }
}
