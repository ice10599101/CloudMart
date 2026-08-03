package com.cloudmart.admin.feign;

import com.cloudmart.admin.dto.feign.CategoryDTO;
import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(contextId = "categoryFeignClient", name = "mall-product", path = "/admin/categories", fallbackFactory = CategoryFeignClientFallbackFactory.class)
public interface CategoryFeignClient {

    @GetMapping
    ApiResponse<List<CategoryDTO>> listCategories();

    @PostMapping
    ApiResponse<CategoryDTO> createCategory(@RequestParam String name, @RequestParam(required = false) Long parentId);

    @PutMapping("/{id}")
    ApiResponse<CategoryDTO> updateCategory(@PathVariable("id") Long id, @RequestParam String name,
                                             @RequestParam(value = "parentId", required = false) Long parentId,
                                             @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                                             @RequestParam(value = "status", required = false) Integer status);

    @DeleteMapping("/{id}")
    ApiResponse<Void> deleteCategory(@PathVariable("id") Long id);
}
