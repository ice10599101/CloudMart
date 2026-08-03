package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
@Tag(name = "分类管理", description = "商品分类查询与创建接口")
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

    @PostMapping
    @Operation(summary = "创建分类", description = "创建新的商品分类")
    public ApiResponse<CategoryVO> createCategory(
            @Parameter(description = "分类名称", required = true) @RequestParam String name,
            @Parameter(description = "父分类ID") @RequestParam(required = false) Long parentId) {
        CategoryDTO dto = productService.createCategory(name, parentId);
        return ApiResponse.ok(productConverter.categoryDtoToVO(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分类", description = "更新商品分类信息")
    public ApiResponse<CategoryVO> updateCategory(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id,
            @Parameter(description = "分类名称", required = true) @RequestParam String name,
            @Parameter(description = "父分类ID") @RequestParam(required = false) Long parentId) {
        CategoryDTO dto = productService.updateCategory(id, name, parentId, null, null);
        return ApiResponse.ok(productConverter.categoryDtoToVO(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类", description = "删除商品分类")
    public ApiResponse<Void> deleteCategory(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id) {
        productService.deleteCategory(id);
        return ApiResponse.ok(null);
    }
}
