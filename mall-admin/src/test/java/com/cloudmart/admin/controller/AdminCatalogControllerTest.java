package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.CategoryDTO;
import com.cloudmart.admin.dto.feign.CreateProductRequest;
import com.cloudmart.admin.dto.feign.ProductDTO;
import com.cloudmart.admin.dto.feign.ProductSearchResultDTO;
import com.cloudmart.admin.dto.feign.UpdateProductRequest;
import com.cloudmart.admin.feign.BrandFeignClient;
import com.cloudmart.admin.feign.CategoryFeignClient;
import com.cloudmart.admin.feign.ProductEsFeignClient;
import com.cloudmart.admin.feign.ProductFeignClient;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCatalogControllerTest {

    private MockMvc mockMvc;
    private ProductFeignClient productFeignClient;
    private CategoryFeignClient categoryFeignClient;
    private BrandFeignClient brandFeignClient;

    @BeforeEach
    void setUp() {
        productFeignClient = mock(ProductFeignClient.class);
        categoryFeignClient = mock(CategoryFeignClient.class);
        brandFeignClient = mock(BrandFeignClient.class);

        AdminCatalogController controller = new AdminCatalogController(
                brandFeignClient,
                productFeignClient,
                categoryFeignClient,
                mock(ProductEsFeignClient.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listBrands_returnsBrandList() throws Exception {
        given(brandFeignClient.listBrands(any(Map.class)))
                .willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/brands").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getBrand_returnsBrandDetail() throws Exception {
        given(brandFeignClient.getBrand(1L)).willReturn(ApiResponse.ok("brand"));

        mockMvc.perform(get("/business/brands/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createBrand_brandCreatedSuccessfully() throws Exception {
        given(brandFeignClient.createBrand(any(Map.class)))
                .willReturn(ApiResponse.ok("brand"));

        mockMvc.perform(post("/business/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestBrand\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateBrand_brandUpdatedSuccessfully() throws Exception {
        given(brandFeignClient.updateBrand(anyLong(), any(Map.class)))
                .willReturn(ApiResponse.ok("brand"));

        mockMvc.perform(put("/business/brands/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"TestBrand\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listProducts_returnsProductList() throws Exception {
        given(productFeignClient.searchProducts(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(ApiResponse.ok(new ProductSearchResultDTO(List.of(), List.of(), List.of(), 0, 1, 10)));

        mockMvc.perform(get("/business/products").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void getProduct_returnsProductDetail() throws Exception {
        ProductDTO product = new ProductDTO(1L, "Test", "desc", 1L, "cat", "brand", "img", 1, List.of(), LocalDateTime.now());
        given(productFeignClient.getProductById(1L))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(get("/business/products/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createProduct_productCreatedSuccessfully() throws Exception {
        ProductDTO product = new ProductDTO(1L, "Test", "desc", 1L, "cat", "brand", "img", 1, List.of(), LocalDateTime.now());
        given(productFeignClient.createProduct(any(CreateProductRequest.class)))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(post("/business/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Product\",\"price\":99.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateProduct_productUpdatedSuccessfully() throws Exception {
        ProductDTO product = new ProductDTO(1L, "Updated", "desc", 1L, "cat", "brand", "img", 1, List.of(), LocalDateTime.now());
        given(productFeignClient.updateProduct(anyLong(), any(UpdateProductRequest.class)))
                .willReturn(ApiResponse.ok(product));

        mockMvc.perform(put("/business/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteProduct_productDeletedSuccessfully() throws Exception {
        given(productFeignClient.deleteProduct(1L)).willReturn(ApiResponse.ok(null));

        mockMvc.perform(delete("/business/products/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listCategories_returnsCategoryList() throws Exception {
        given(categoryFeignClient.listCategories()).willReturn(ApiResponse.ok(List.of()));

        mockMvc.perform(get("/business/categories").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createCategory_categoryCreatedSuccessfully() throws Exception {
        CategoryDTO category = new CategoryDTO(1L, "电子产品", 0L, 1, "icon", 1);
        given(categoryFeignClient.createCategory("电子产品", null))
                .willReturn(ApiResponse.ok(category));

        mockMvc.perform(post("/business/categories")
                        .param("name", "电子产品")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteCategory_categoryDeletedSuccessfully() throws Exception {
        given(categoryFeignClient.deleteCategory(1L)).willReturn(ApiResponse.ok(null));

        mockMvc.perform(delete("/business/categories/1").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
