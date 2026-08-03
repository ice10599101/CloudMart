package com.cloudmart.product.controller;

import com.cloudmart.common.handler.GlobalExceptionHandler;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.CategoryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminCategoryControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductService productService = Mockito.mock(ProductService.class);
    private final ProductConverter productConverter = Mockito.mock(ProductConverter.class);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCategoryController(productService, productConverter))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private CategoryDTO buildCategoryDTO() {
        return new CategoryDTO(1L, "电子产品", null, 1, "icon-electronics", 1);
    }

    private CategoryVO buildCategoryVO() {
        return new CategoryVO(1L, "电子产品", null, 1, "icon-electronics", 1);
    }

    @Nested
    @DisplayName("GET /admin/categories - 分类列表")
    class ListCategories {

        @Test
        @DisplayName("查询所有分类成功")
        void shouldReturnAllCategories() throws Exception {
            CategoryDTO dto = buildCategoryDTO();
            CategoryVO vo = buildCategoryVO();
            given(productService.listCategories()).willReturn(List.of(dto));
            given(productConverter.categoryDtoListToVOList(List.of(dto))).willReturn(List.of(vo));

            mockMvc.perform(get("/admin/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].id").value(1))
                    .andExpect(jsonPath("$.data[0].name").value("电子产品"));
        }
    }

    @Nested
    @DisplayName("POST /admin/categories - 创建分类")
    class CreateCategory {

        @Test
        @DisplayName("创建分类成功")
        void shouldCreateCategory() throws Exception {
            CategoryDTO dto = new CategoryDTO(2L, "新分类", null, 2, "icon-new", 1);
            CategoryVO vo = new CategoryVO(2L, "新分类", null, 2, "icon-new", 1);
            given(productService.createCategory("新分类", null)).willReturn(dto);
            given(productConverter.categoryDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(post("/admin/categories")
                            .param("name", "新分类"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(2))
                    .andExpect(jsonPath("$.data.name").value("新分类"));
        }
    }

    @Nested
    @DisplayName("PUT /admin/categories/{id} - 更新分类")
    class UpdateCategory {

        @Test
        @DisplayName("更新分类成功")
        void shouldUpdateCategory() throws Exception {
            CategoryDTO dto = new CategoryDTO(1L, "更新分类", null, 3, "icon-updated", 1);
            CategoryVO vo = new CategoryVO(1L, "更新分类", null, 3, "icon-updated", 1);
            given(productService.updateCategory(1L, "更新分类", null, 3, 1)).willReturn(dto);
            given(productConverter.categoryDtoToVO(dto)).willReturn(vo);

            mockMvc.perform(put("/admin/categories/1")
                            .param("name", "更新分类")
                            .param("sortOrder", "3")
                            .param("status", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("更新分类"));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/categories/{id} - 删除分类")
    class DeleteCategory {

        @Test
        @DisplayName("删除分类成功")
        void shouldDeleteCategory() throws Exception {
            willDoNothing().given(productService).deleteCategory(1L);

            mockMvc.perform(delete("/admin/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }
}
