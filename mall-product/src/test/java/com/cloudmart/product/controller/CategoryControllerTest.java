package com.cloudmart.product.controller;

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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 分类公开查询接口测试（SEC-01）：分类创建/更新/删除写入口已随身份边界改造移除
 * （管理动作统一走 AdminCategoryController），此处仅覆盖保留的只读端点。
 */
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
}
