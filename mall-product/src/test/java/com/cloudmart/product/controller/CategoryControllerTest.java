package com.cloudmart.product.controller;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.CategoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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

class CategoryControllerTest {

    private MockMvc mockMvc;

    private final ProductService productService = Mockito.mock(ProductService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(productService, productConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CategoryDTO buildCategoryDTO() {
        return new CategoryDTO(1L, "电子产品", null, 1, "icon.png", 1);
    }

    private CategoryVO buildCategoryVO() {
        return new CategoryVO(1L, "电子产品", null, 1, "icon.png", 1);
    }

    @Test
    @DisplayName("GET /categories - 分类列表返回信封格式")
    void listCategories_ShouldReturnSuccessEnvelope() throws Exception {
        CategoryDTO dto = buildCategoryDTO();
        CategoryVO vo = buildCategoryVO();
        given(productService.listCategories()).willReturn(List.of(dto));
        given(productConverter.categoryDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

        mockMvc.perform(get("/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("电子产品"));
    }

    @Test
    @DisplayName("POST /categories - 创建分类返回信封格式")
    void createCategory_ShouldReturnSuccessEnvelope() throws Exception {
        CategoryDTO dto = buildCategoryDTO();
        CategoryVO vo = buildCategoryVO();
        given(productService.createCategory("电子产品", null)).willReturn(dto);
        given(productConverter.categoryDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/categories")
                        .param("name", "电子产品"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("电子产品"));
    }

    @Test
    @DisplayName("POST /categories - 带父分类创建返回信封格式")
    void createCategory_WithParentId_ShouldReturnSuccessEnvelope() throws Exception {
        CategoryDTO dto = new CategoryDTO(2L, "手机", 1L, 1, null, 1);
        CategoryVO vo = new CategoryVO(2L, "手机", 1L, 1, null, 1);
        given(productService.createCategory("手机", 1L)).willReturn(dto);
        given(productConverter.categoryDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(post("/categories")
                        .param("name", "手机")
                        .param("parentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.parentId").value(1));
    }

    @Test
    @DisplayName("POST /categories - 分类名重复返回错误信封")
    void createCategory_WhenDuplicateName_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("VALIDATION_ERROR", "分类名称已存在"))
                .given(productService).createCategory("电子产品", null);

        mockMvc.perform(post("/categories")
                        .param("name", "电子产品"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /categories/{id} - 更新分类返回信封格式")
    void updateCategory_ShouldReturnSuccessEnvelope() throws Exception {
        CategoryDTO dto = new CategoryDTO(1L, "数码产品", null, 1, "icon.png", 1);
        CategoryVO vo = new CategoryVO(1L, "数码产品", null, 1, "icon.png", 1);
        given(productService.updateCategory(1L, "数码产品", null, null, null)).willReturn(dto);
        given(productConverter.categoryDtoToVO(dto)).willReturn(vo);

        mockMvc.perform(put("/categories/1")
                        .param("name", "数码产品"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.name").value("数码产品"));
    }

    @Test
    @DisplayName("PUT /categories/{id} - 分类不存在返回错误信封")
    void updateCategory_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("USER_NOT_FOUND", "分类不存在"))
                .given(productService).updateCategory(eq(999L), eq("测试"), any(), any(), any());

        mockMvc.perform(put("/categories/999")
                        .param("name", "测试"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /categories/{id} - 删除分类返回信封格式")
    void deleteCategory_ShouldReturnSuccessEnvelope() throws Exception {
        willDoNothing().given(productService).deleteCategory(1L);

        mockMvc.perform(delete("/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /categories/{id} - 分类不存在返回错误信封")
    void deleteCategory_WhenNotFound_ShouldReturnErrorEnvelope() throws Exception {
        willThrow(new BusinessException("USER_NOT_FOUND", "分类不存在"))
                .given(productService).deleteCategory(999L);

        mockMvc.perform(delete("/categories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }
}
