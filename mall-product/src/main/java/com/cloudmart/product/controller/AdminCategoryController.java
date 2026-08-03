package com.cloudmart.product.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.product.converter.ProductConverter;
import com.cloudmart.product.dto.CategoryDTO;
import com.cloudmart.product.service.ProductService;
import com.cloudmart.product.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/categories")
@Tag(name = "分类管理(后台)", description = "管理后台分类管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ProductService productService;
    private final ProductConverter productConverter;

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "分类列表", description = "管理后台查询所有商品分类")
    public ApiResponse<List<CategoryVO>> listCategories() {
        List<CategoryDTO> dtos = productService.listCategories();
        return ApiResponse.ok(productConverter.categoryDtoListToVOList(dtos));
    }

    @PostMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "创建分类", description = "管理后台创建商品分类")
    public ApiResponse<CategoryVO> createCategory(
            @Parameter(description = "分类名称", required = true) @RequestParam String name,
            @Parameter(description = "父分类ID") @RequestParam(required = false) Long parentId) {
        CategoryDTO dto = productService.createCategory(name, parentId);
        return ApiResponse.ok(productConverter.categoryDtoToVO(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "更新分类", description = "管理后台更新商品分类信息")
    public ApiResponse<CategoryVO> updateCategory(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id,
            @Parameter(description = "分类名称", required = true) @RequestParam String name,
            @Parameter(description = "父分类ID") @RequestParam(required = false) Long parentId,
            @Parameter(description = "排序") @RequestParam(required = false) Integer sortOrder,
            @Parameter(description = "状态(0-停用,1-正常)") @RequestParam(required = false) Integer status) {
        CategoryDTO dto = productService.updateCategory(id, name, parentId, sortOrder, status);
        return ApiResponse.ok(productConverter.categoryDtoToVO(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "删除分类", description = "管理后台删除商品分类")
    public ApiResponse<Void> deleteCategory(
            @Parameter(description = "分类ID", required = true) @PathVariable Long id) {
        productService.deleteCategory(id);
        return ApiResponse.ok(null);
    }
}
