package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.wish.service.CategoryService;
import com.cloudmart.wish.vo.CategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 心愿分类 Controller（用户端字典查询）。
 *
 * <p>管理后台 CRUD 见 {@link AdminCategoryController}。</p>
 */
@RestController
@RequestMapping("/categories")
@Tag(name = "心愿分类", description = "心愿分类字典查询")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类字典", description = "获取全部分类列表（Redis 缓存），按 sort 升序")
    public ApiResponse<List<CategoryVO>> listCategories() {
        List<CategoryVO> categories = categoryService.listCategories();
        return ApiResponse.ok(categories);
    }
}
