package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类公开查询接口（SEC-01）：仅保留匿名可浏览的分类列表。
 * 分类的创建/更新/删除统一走 {@link AdminCategoryController}
 * （mall-admin 经服务令牌调用）；原公开写入口已随 SEC-01 移除
 * （此前 /categories 的 permitAll 未限定方法，匿名即可创建分类）。
 */
@RestController
@RequestMapping("/categories")
@Tag(name = "分类查询", description = "商品分类查询接口")
public class CategoryController {

    private final ProductService productService;
    private final ProductConverter productConverter;

    public CategoryController(ProductService productService, ProductConverter productConverter) {
        this.productService = productService;
        this.productConverter = productConverter;
    }

    @GetMapping
    @Operation(summary = "分类列表", description = "查询所有商品分类")
    public ApiResponse<List<CategoryVO>> listCategories() {
        List<CategoryDTO> dtos = productService.listCategories();
        return ApiResponse.ok(productConverter.categoryDtoListToVOList(dtos));
    }
}
