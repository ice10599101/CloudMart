package com.cloudmart.risk.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.risk.service.RiskRecordService;
import com.cloudmart.risk.vo.RiskRecordVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RiskRecordControllerTest {

    private MockMvc mockMvc;

    private final RiskRecordService riskRecordService = Mockito.mock(RiskRecordService.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RiskRecordController(riskRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("查询风控记录 - 成功返回信封")
    void listRecords_ShouldReturnEnvelope() throws Exception {
        RiskRecordVO vo = new RiskRecordVO(1L, 100L, "ORDER", "HIGH", "频繁下单", FIXED_TIME);

        given(riskRecordService.listRecords(null, 1, 20)).willReturn(List.of(vo));

        mockMvc.perform(get("/records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(100))
                .andExpect(jsonPath("$.data[0].riskLevel").value("HIGH"));
    }

    @Test
    @DisplayName("查询风控记录 - 按用户ID筛选返回信封")
    void listRecords_WithUserIdFilter_ShouldReturnEnvelope() throws Exception {
        RiskRecordVO vo = new RiskRecordVO(1L, 100L, "ORDER", "HIGH", "频繁下单", FIXED_TIME);

        given(riskRecordService.listRecords(100L, 1, 20)).willReturn(List.of(vo));

        mockMvc.perform(get("/records")
                        .param("userId", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].userId").value(100));
    }

    @Test
    @DisplayName("查询风控记录详情 - 成功返回信封")
    void getRecord_ShouldReturnEnvelope() throws Exception {
        RiskRecordVO vo = new RiskRecordVO(1L, 100L, "ORDER", "HIGH", "频繁下单", FIXED_TIME);

        given(riskRecordService.getRecord(1L)).willReturn(vo);

        mockMvc.perform(get("/records/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.action").value("ORDER"))
                .andExpect(jsonPath("$.data.reason").value("频繁下单"));
    }
}
