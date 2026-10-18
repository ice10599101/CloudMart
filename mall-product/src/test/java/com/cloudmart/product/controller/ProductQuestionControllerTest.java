package com.cloudmart.product.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.service.ProductQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T13：提问/回答的入参校验——@NotBlank/@Size 必须落在 String 组件上，
 * 空白与超长请求应返回 400（VALIDATION_ERROR），不得落入 service 或 500。
 */
class ProductQuestionControllerTest {

    private MockMvc mockMvc;
    private final ProductQuestionService questionService = Mockito.mock(ProductQuestionService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductQuestionController(questionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("提问内容为空白 → 400 VALIDATION_ERROR（不进入 service）")
    void ask_blankQuestion_returns400() throws Exception {
        mockMvc.perform(post("/products/1/questions")
                        .header("X-User-Id", "8901")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(questionService, never()).ask(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("提问内容超 500 字 → 400 VALIDATION_ERROR")
    void ask_overlongQuestion_returns400() throws Exception {
        String overlong = "问".repeat(501);
        mockMvc.perform(post("/products/1/questions")
                        .header("X-User-Id", "8901")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + overlong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(questionService, never()).ask(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("回答内容为空白 → 400 VALIDATION_ERROR")
    void answer_blankAnswer_returns400() throws Exception {
        mockMvc.perform(post("/products/1/questions/1/answer")
                        .header("X-User-Id", "8901")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        verify(questionService, never()).answer(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }
}
