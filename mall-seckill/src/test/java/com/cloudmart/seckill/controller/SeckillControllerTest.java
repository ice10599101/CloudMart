package com.cloudmart.seckill.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.SeckillExecuteRequest;
import com.cloudmart.seckill.dto.SeckillResultDTO;
import com.cloudmart.seckill.service.SeckillExecuteService;
import com.cloudmart.seckill.vo.SeckillResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeckillControllerTest {

    private MockMvc mockMvc;

    private final SeckillExecuteService seckillExecuteService = Mockito.mock(SeckillExecuteService.class);
    private final SeckillConverter seckillConverter = Mockito.mock(SeckillConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SeckillController(seckillExecuteService, seckillConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("执行秒杀 - 成功返回信封格式")
    void executeSeckill_ShouldReturn200WithEnvelope() throws Exception {
        SeckillResultDTO dto = new SeckillResultDTO("SUCCESS", 1001L, "秒杀成功");
        SeckillExecuteRequest request = new SeckillExecuteRequest(1L, 10L);

        given(seckillExecuteService.executeSeckill(eq(1L), Mockito.any(SeckillExecuteRequest.class))).willReturn(dto);

        SeckillResultVO vo = new SeckillResultVO(true, "ORD1001", "秒杀成功");
        given(seckillConverter.resultDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/execute")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"seckillProductId\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.message").value("秒杀成功"));
    }

    @Test
    @DisplayName("查询秒杀结果 - 成功返回信封格式")
    void getSeckillResult_ShouldReturn200WithEnvelope() throws Exception {
        SeckillResultDTO dto = new SeckillResultDTO("SUCCESS", 1001L, "秒杀成功");

        given(seckillExecuteService.getSeckillResult(1L, 1L, 10L)).willReturn(dto);

        SeckillResultVO vo = new SeckillResultVO(true, "ORD1001", "秒杀成功");
        given(seckillConverter.resultDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/result")
                        .header("X-User-Id", 1)
                        .param("activityId", "1")
                        .param("seckillProductId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));
    }

    @Test
    @DisplayName("执行秒杀 - 库存不足返回错误信封")
    void executeSeckill_WhenOutOfStock_ShouldReturnErrorEnvelope() throws Exception {
        given(seckillExecuteService.executeSeckill(eq(1L), Mockito.any(SeckillExecuteRequest.class)))
                .willThrow(new BusinessException("SECKILL_OUT_OF_STOCK", "秒杀商品已售罄"));

        mockMvc.perform(post("/execute")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"seckillProductId\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SECKILL_OUT_OF_STOCK"))
                .andExpect(jsonPath("$.error.message").value("秒杀商品已售罄"));
    }

    @Test
    @DisplayName("执行秒杀 - 缺少必填字段返回校验错误")
    void executeSeckill_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/execute")
                        .header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("执行秒杀 - 缺少X-User-Id返回401")
    void executeSeckill_WhenMissingUserId_ShouldReturn401() throws Exception {
        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1,\"seckillProductId\":10}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
