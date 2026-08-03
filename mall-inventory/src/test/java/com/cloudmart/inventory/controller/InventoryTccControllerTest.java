package com.cloudmart.inventory.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.inventory.dto.TccDeductRequest;
import com.cloudmart.inventory.service.InventoryTccService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InventoryTccControllerTest {

    private MockMvc mockMvc;

    private final InventoryTccService tccService = Mockito.mock(InventoryTccService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventoryTccController(tccService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("Try冻结库存 - 成功返回信封")
    void tryDeduct_ShouldReturnEnvelope() throws Exception {
        given(tccService.tryDeduct(Mockito.any(TccDeductRequest.class))).willReturn("tx-123");

        mockMvc.perform(post("/tcc/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":200,\"productId\":100,\"quantity\":5,\"orderId\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("tx-123"));
    }

    @Test
    @DisplayName("Try冻结库存 - 缺少必填字段返回校验错误")
    void tryDeduct_WhenMissingRequiredField_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/tcc/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Confirm确认扣减 - 成功返回信封")
    void confirmDeduct_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/tcc/confirm/tx-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tccService).confirmDeduct("tx-123");
    }

    @Test
    @DisplayName("Cancel取消冻结 - 成功返回信封")
    void cancelDeduct_ShouldReturnEnvelope() throws Exception {
        mockMvc.perform(post("/tcc/cancel/tx-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tccService).cancelDeduct("tx-123");
    }
}
