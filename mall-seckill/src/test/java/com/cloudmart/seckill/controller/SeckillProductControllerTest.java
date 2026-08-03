package com.cloudmart.seckill.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.vo.SeckillProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeckillProductControllerTest {

    private MockMvc mockMvc;

    private final SeckillProductService productService = Mockito.mock(SeckillProductService.class);
    private final SeckillConverter seckillConverter = Mockito.mock(SeckillConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SeckillProductController(productService, seckillConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("添加秒杀商品 - 成功返回信封格式")
    void addProduct_ShouldReturn200WithEnvelope() throws Exception {
        SeckillProductDTO dto = new SeckillProductDTO(1L, 1L, 100L,
                new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1, "ACTIVE", LocalDateTime.now());

        given(productService.addProduct(Mockito.eq(1L), Mockito.any(AddSeckillProductRequest.class))).willReturn(dto);

        SeckillProductVO vo = new SeckillProductVO(1L, 200L, "商品A", "img.jpg",
                new BigDecimal("99.00"), new BigDecimal("199.00"), 80, 100, 1);
        given(seckillConverter.productDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuId\":100,\"seckillPrice\":99.00,\"originalPrice\":199.00,\"totalStock\":100,\"perUserLimit\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.seckillPrice").value(99.00));
    }

    @Test
    @DisplayName("查询活动下的秒杀商品 - 成功返回信封格式")
    void listProductsByActivity_ShouldReturn200WithEnvelope() throws Exception {
        SeckillProductDTO dto = new SeckillProductDTO(1L, 1L, 100L,
                new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1, "ACTIVE", LocalDateTime.now());

        given(productService.listProductsByActivity(1L)).willReturn(List.of(dto));

        SeckillProductVO vo = new SeckillProductVO(1L, 200L, "商品A", "img.jpg",
                new BigDecimal("99.00"), new BigDecimal("199.00"), 80, 100, 1);
        given(seckillConverter.productDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/products/activity/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    @DisplayName("查询秒杀商品详情 - 成功返回信封格式")
    void getProduct_ShouldReturn200WithEnvelope() throws Exception {
        SeckillProductDTO dto = new SeckillProductDTO(1L, 1L, 100L,
                new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1, "ACTIVE", LocalDateTime.now());

        given(productService.getProduct(1L)).willReturn(dto);

        SeckillProductVO vo = new SeckillProductVO(1L, 200L, "商品A", "img.jpg",
                new BigDecimal("99.00"), new BigDecimal("199.00"), 80, 100, 1);
        given(seckillConverter.productDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.availableStock").value(80));
    }

    @Test
    @DisplayName("查询不存在的秒杀商品 - 返回错误信封")
    void getProduct_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        given(productService.getProduct(999L))
                .willThrow(new BusinessException("SECKILL_PRODUCT_NOT_FOUND", "秒杀商品不存在"));

        mockMvc.perform(get("/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SECKILL_PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("秒杀商品不存在"));
    }

    @Test
    @DisplayName("添加秒杀商品 - 缺少必填字段返回校验错误")
    void addProduct_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
