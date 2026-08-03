package com.cloudmart.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CreateProductRequest;
import com.cloudmart.product.dto.ProductDTO;
import com.cloudmart.product.dto.ProductSearchRequest;
import com.cloudmart.product.dto.UpdateProductRequest;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.ProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProductControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductService productService = Mockito.mock(ProductService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);

    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2026, 5, 29, 10, 0);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminProductController(productService, productConverter))
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

    @Nested
    @DisplayName("GET /admin/products/count - 商品总数")
    class GetProductCount {

        @Test
        @DisplayName("获取商品总数成功")
        void shouldReturnProductCount() throws Exception {
            given(productService.getProductCount()).willReturn(42L);

            mockMvc.perform(get("/admin/products/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.count").value(42));
        }
    }

    @Nested
    @DisplayName("GET /admin/products/{id} - 查询商品详情")
    class GetProductById {

        @Test
        @DisplayName("查询商品详情成功")
        void shouldReturnProductDetail() throws Exception {
            ProductDTO dto = buildProductDTO();
            ProductVO vo = buildProductVO();
            given(productService.getProductById(1L)).willReturn(dto);
            given(productConverter.productDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(get("/admin/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("测试商品"));
        }
    }

    @Nested
    @DisplayName("POST /admin/products - 创建商品")
    class CreateProduct {

        @Test
        @DisplayName("创建商品成功")
        void shouldCreateProduct() throws Exception {
            CreateProductRequest request = new CreateProductRequest("新商品", "描述", 10L, "品牌A", "image.jpg", null);
            ProductDTO dto = buildProductDTO();
            ProductVO vo = buildProductVO();
            given(productService.createProduct(any(CreateProductRequest.class))).willReturn(dto);
            given(productConverter.productDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(post("/admin/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.name").value("测试商品"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/products/{id} - 更新商品")
    class UpdateProduct {

        @Test
        @DisplayName("更新商品成功")
        void shouldUpdateProduct() throws Exception {
            UpdateProductRequest request = new UpdateProductRequest("更新商品", "新描述", 10L, "品牌A", "new-image.jpg", 1, null);
            ProductDTO dto = buildProductDTO();
            ProductVO vo = buildProductVO();
            given(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).willReturn(dto);
            given(productConverter.productDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(put("/admin/products/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/products/{id} - 删除商品")
    class DeleteProduct {

        @Test
        @DisplayName("删除商品成功")
        void shouldDeleteProduct() throws Exception {
            willDoNothing().given(productService).deleteProduct(1L);

            mockMvc.perform(delete("/admin/products/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
