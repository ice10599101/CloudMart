package com.cloudmart.product.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchResponse;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.ProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品公开查询接口测试（SEC-01）：商品创建/更新/删除写入口已随身份边界改造
 * 移除（管理动作统一走 AdminProductController），此处仅覆盖保留的只读端点。
 */
class ProductControllerTest {

    private MockMvc mockMvc;

    private final ProductService productService = Mockito.mock(ProductService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService, productConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ProductDTO buildProductDTO() {
        return new ProductDTO(1L, "测试商品", "商品描述", 10L, "电子产品",
                "品牌A", "image.jpg", 1, List.of(), FIXED_TIME);
    }

    private ProductVO buildProductVO() {
        return new ProductVO(1L, "测试商品", "image.jpg",
                new BigDecimal("99.00"), new BigDecimal("129.00"),
                0, 100, "电子产品", "品牌A", 1, FIXED_TIME, java.util.List.of());
    }

    @Test
    @DisplayName("GET /products/{id} - 查询商品返回信封格式")
    void getProductById_ShouldReturnSuccessEnvelope() throws Exception {
        ProductDTO dto = buildProductDTO();
        ProductVO vo = buildProductVO();
        given(productService.getProductById(1L)).willReturn(dto);
        given(productConverter.productDetailToVO(dto)).willReturn(vo);

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试商品"));
    }

    @Test
    @DisplayName("GET /products/{id} - 商品不存在返回错误信封")
    void getProductById_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("USER_NOT_FOUND", "商品不存在"))
                .given(productService).getProductById(999L);

        mockMvc.perform(get("/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /products/search - 搜索商品返回信封格式含meta")
    void searchProducts_ShouldReturnSuccessEnvelopeWithMeta() throws Exception {
        ProductDTO dto = buildProductDTO();
        ProductSearchResponse response = new ProductSearchResponse(
                List.of(dto),
                List.of(new ProductSearchResponse.BrandBucket("测试品牌", 1L)),
                List.of(new ProductSearchResponse.CategoryBucket(100L, 1L)),
                1L,
                1,
                20
        );
        given(productService.searchProducts(any())).willReturn(response);

        ProductVO vo = buildProductVO();
        given(productConverter.productDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/products/search")
                        .param("keyword", "测试")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.products").isArray())
                .andExpect(jsonPath("$.data.products[0].id").value(1))
                .andExpect(jsonPath("$.data.brands[0].brand").value("测试品牌"))
                .andExpect(jsonPath("$.data.brands[0].count").value(1))
                .andExpect(jsonPath("$.data.categories[0].categoryId").value(100))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(20))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    @DisplayName("GET /products/search - 搜索服务不可用返回错误信封")
    void searchProducts_WhenServiceUnavailable_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("PRODUCT_SERVICE_UNAVAILABLE", "商品搜索服务不可用"))
                .given(productService).searchProducts(any());

        mockMvc.perform(get("/products/search")
                        .param("keyword", "测试"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PRODUCT_SERVICE_UNAVAILABLE"));
    }
}
