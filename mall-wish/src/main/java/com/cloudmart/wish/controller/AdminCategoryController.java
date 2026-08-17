package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.dto.CreateCategoryRequest;
import com.cloudmart.wish.dto.UpdateCategoryRequest;
import com.cloudmart.wish.service.CategoryService;
import com.cloudmart.wish.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台心愿分类 Controller。
 *
 * <p>路由前缀 /admin/categories，需管理员权限（网关层 X-Admin-Role 校验）。</p>
 */
@RestController
@RequestMapping("/admin/categories")
@Tag(name = "管理后台-分类管理", description = "心愿分类 CRUD")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类列表", description = "获取全部分类列表（管理后台）")
    public ApiResponse<java.util.List<CategoryVO>> listCategories() {
        return ApiResponse.ok(categoryService.listCategories());
    }

    @PostMapping
    @Operation(summary = "创建分类", description = "创建新的心愿分类，code 唯一")
    public ApiResponse<CategoryVO> createCategory(
            @Parameter(description = "创建分类请求") @Valid @RequestBody CreateCategoryRequest request) {
        CategoryVO vo = categoryService.createCategory(request);
        return ApiResponse.ok(vo);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分类", description = "更新分类名称/排序/图标")
    public ApiResponse<CategoryVO> updateCategory(
            @Parameter(description = "分类 ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "更新请求") @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryVO vo = categoryService.updateCategory(id, request);
        return ApiResponse.ok(vo);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类", description = "删除自定义分类（系统预设分类不可删除）")
    public ApiResponse<Void> deleteCategory(
            @Parameter(description = "分类 ID", required = true) @PathVariable("id") Long id) {
        categoryService.deleteCategory(id);
        return ApiResponse.ok(null);
    }
}
