package com.cloudmart.product.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CreateProductRequest;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchResponse;
import com.cloudmart.product.dto.UpdateProductRequest;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.ProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                0, 100, "电子产品", "品牌A", 1, FIXED_TIME);
    }

    @Test
    @DisplayName("POST /products - 创建商品返回信封格式")
    void createProduct_ShouldReturnSuccessEnvelope() throws Exception {
        ProductDTO dto = buildProductDTO();
        ProductVO vo = buildProductVO();
        given(productService.createProduct(any(CreateProductRequest.class))).willReturn(dto);
        given(productConverter.productDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateProductRequest("测试商品", "商品描述", 10L, "品牌A", "image.jpg", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("测试商品"));
    }

    @Test
    @DisplayName("POST /products - 名称校验失败返回VALIDATION_ERROR")
    void createProduct_WhenInvalidInput_ShouldReturnValidationError() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"desc\",\"categoryId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /products/{id} - 查询商品返回信封格式")
    void getProductById_ShouldReturnSuccessEnvelope() throws Exception {
        ProductDTO dto = buildProductDTO();
        ProductVO vo = buildProductVO();
        given(productService.getProductById(1L)).willReturn(dto);
        given(productConverter.productDtoToVO(dto)).willReturn(vo);

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
    @DisplayName("PUT /products/{id} - 更新商品返回信封格式")
    void updateProduct_ShouldReturnSuccessEnvelope() throws Exception {
        ProductDTO dto = buildProductDTO();
        ProductVO vo = buildProductVO();
        given(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).willReturn(dto);
        given(productConverter.productDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProductRequest("更新商品", "新描述", 10L, "品牌A", "new-image.jpg", 1, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("PUT /products/{id} - 商品不存在返回错误信封")
    void updateProduct_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("USER_NOT_FOUND", "商品不存在"))
                .given(productService).updateProduct(eq(999L), any(UpdateProductRequest.class));

        mockMvc.perform(put("/products/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateProductRequest("更新商品", null, null, null, null, null, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /products/{id} - 删除商品返回信封格式")
    void deleteProduct_ShouldReturnSuccessEnvelope() throws Exception {
        willDoNothing().given(productService).deleteProduct(1L);

        mockMvc.perform(delete("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /products/{id} - 商品不存在返回错误信封")
    void deleteProduct_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("USER_NOT_FOUND", "商品不存在"))
                .given(productService).deleteProduct(999L);

        mockMvc.perform(delete("/products/999"))
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
