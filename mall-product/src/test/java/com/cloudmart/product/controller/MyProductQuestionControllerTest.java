package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.service.ProductQuestionService;
import com.cloudmart.product.vo.ProductAnswerVO;
import com.cloudmart.product.vo.ProductQuestionVO;
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

/**
 * T13：跨商品「我的提问」独立路由（无 productId）。
 * 返回统一列表契约：data 数组 + meta{page,pageSize,total}。
 */
class MyProductQuestionControllerTest {

    private MockMvc mockMvc;
    private final ProductQuestionService questionService = Mockito.mock(ProductQuestionService.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new MyProductQuestionController(questionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /products/questions/my 返回数组+meta（不含 productId）")
    void myQuestions_returnsArrayWithMeta() throws Exception {
        ProductQuestionVO vo = new ProductQuestionVO(1L, 100L, 8901L, "提问者", "这条裙子怎么样？",
                List.of(new ProductAnswerVO(2L, 8902L, "回答者", "不错", false, LocalDateTime.now())),
                LocalDateTime.now());
        Page<ProductQuestionVO> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(vo));
        given(questionService.myQuestions(8901L, 1, 10)).willReturn(page);

        mockMvc.perform(get("/products/questions/my").header("X-User-Id", "8901"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].productId").value(100))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(10));
    }
}
