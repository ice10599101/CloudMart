package com.cloudmart.seckill.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.seckill.converter.SeckillConverter;
import com.cloudmart.seckill.dto.AddSeckillProductRequest;
import com.cloudmart.seckill.dto.SeckillProductDTO;
import com.cloudmart.seckill.repository.SeckillProductMapper;
import com.cloudmart.seckill.service.SeckillProductService;
import com.cloudmart.seckill.vo.SeckillProductVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminSeckillProductControllerTest {

    private MockMvc mockMvc;

    private final SeckillProductService productService = Mockito.mock(SeckillProductService.class);
    private final SeckillConverter seckillConverter = Mockito.mock(SeckillConverter.class);
    private final SeckillProductMapper seckillProductMapper = Mockito.mock(SeckillProductMapper.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminSeckillProductController(productService, seckillConverter, seckillProductMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /admin/seckill/products/activity/{activityId}")
    class ListProductsByActivityTests {

        @Test
        @DisplayName("查询活动下的秒杀商品成功返回信封格式")
        void listProductsByActivity_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillProductDTO dto = new SeckillProductDTO(1L, 1L, 100L,
                    new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1, "ON_SHELF", LocalDateTime.now());
            given(productService.listProductsByActivity(1L)).willReturn(List.of(dto));

            SeckillProductVO vo = new SeckillProductVO(1L, 200L, "商品A", "img.jpg",
                    new BigDecimal("99.00"), new BigDecimal("199.00"), 80, 100, 1);
            given(seckillConverter.productDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

            mockMvc.perform(get("/admin/seckill/products/activity/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].seckillPrice").value(99.00));
        }
    }

    @Nested
    @DisplayName("GET /admin/seckill/products/{productId}")
    class GetProductTests {

        @Test
        @DisplayName("查询秒杀商品详情成功返回信封格式")
        void getProduct_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillProductDTO dto = new SeckillProductDTO(1L, 1L, 100L,
                    new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1, "ON_SHELF", LocalDateTime.now());
            given(productService.getProduct(1L)).willReturn(dto);

            SeckillProductVO vo = new SeckillProductVO(1L, 200L, "商品A", "img.jpg",
                    new BigDecimal("99.00"), new BigDecimal("199.00"), 80, 100, 1);
            given(seckillConverter.productDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(get("/admin/seckill/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.availableStock").value(80));
        }

        @Test
        @DisplayName("商品不存在返回错误信封")
        void getProduct_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
            willThrow(new BusinessException("PRODUCT_NOT_FOUND", "秒杀商品不存在"))
                    .given(productService).getProduct(999L);

            mockMvc.perform(get("/admin/seckill/products/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("POST /admin/seckill/products/{activityId}")
    class AddProductTests {

        @Test
        @DisplayName("添加秒杀商品成功返回信封格式")
        void addProduct_ShouldReturnSuccessEnvelope() throws Exception {
            SeckillProductDTO dto = new SeckillProductDTO(1L, 1L, 100L,
                    new BigDecimal("99.00"), new BigDecimal("199.00"), 100, 80, 1, "ON_SHELF", LocalDateTime.now());
            given(productService.addProduct(Mockito.eq(1L), Mockito.any(AddSeckillProductRequest.class))).willReturn(dto);

            SeckillProductVO vo = new SeckillProductVO(1L, 200L, "商品A", "img.jpg",
                    new BigDecimal("99.00"), new BigDecimal("199.00"), 80, 100, 1);
            given(seckillConverter.productDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(post("/admin/seckill/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"skuId\":100,\"seckillPrice\":99.00,\"originalPrice\":199.00,\"totalStock\":100,\"perUserLimit\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.seckillPrice").value(99.00));
        }

        @Test
        @DisplayName("添加秒杀商品缺少必填字段返回校验错误")
        void addProduct_WhenMissingRequiredFields_ShouldReturnValidationError() throws Exception {
            mockMvc.perform(post("/admin/seckill/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
    }
}
