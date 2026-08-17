package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.feign.AdminWishSearchRequest;
import com.cloudmart.admin.feign.WishFeignClient;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 心愿宇宙管理代理接口。
 *
 * <p>转发至 mall-wish /admin/** 内部端点，由网关 AdminAuthGlobalFilter
 * 校验管理员身份（/api/admin/** 前缀）。</p>
 */
@RestController
@Tag(name = "心愿管理", description = "管理后台心愿宇宙模块代理接口")
@RequiredArgsConstructor
public class AdminWishController {

    private final WishFeignClient wishFeignClient;

    // ========== 心愿列表与审核 ==========

    @GetMapping("/wish/wishes")
    @RequiresPermission("business:wish:list")
    @Operation(summary = "心愿列表", description = "多维度筛选（状态/审核状态/分类/关键词）offset 分页")
    public ApiResponse<Object> listWishes(@Valid AdminWishSearchRequest request) {
        return wishFeignClient.listWishes(request);
    }

    @GetMapping("/wish/wishes/{id}")
    @RequiresPermission("business:wish:query")
    @Operation(summary = "心愿详情", description = "含审核字段与软删时间")
    public ApiResponse<Object> getWish(@PathVariable Long id) {
        return wishFeignClient.getWish(id);
    }

    @PutMapping("/wish/wishes/{id}/audit")
    @OperLog(title = "心愿审核", businessType = 2)
    @RequiresPermission("business:wish:audit")
    @Operation(summary = "审核心愿", description = "PENDING → APPROVED/REJECTED，REJECTED 需填写原因")
    public ApiResponse<Object> auditWish(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return wishFeignClient.auditWish(id, data);
    }

    // ========== 心愿分类管理 ==========

    @GetMapping("/wish/categories")
    @RequiresPermission("business:wishCategory:list")
    @Operation(summary = "心愿分类列表")
    public ApiResponse<Object> listCategories() {
        return wishFeignClient.listCategories();
    }

    @PostMapping("/wish/categories")
    @OperLog(title = "心愿分类管理", businessType = 1)
    @RequiresPermission("business:wishCategory:add")
    @Operation(summary = "创建心愿分类", description = "code 唯一")
    public ApiResponse<Object> createCategory(@RequestBody Map<String, Object> data) {
        return wishFeignClient.createCategory(data);
    }

    @PutMapping("/wish/categories/{id}")
    @OperLog(title = "心愿分类管理", businessType = 2)
    @RequiresPermission("business:wishCategory:edit")
    @Operation(summary = "更新心愿分类")
    public ApiResponse<Object> updateCategory(@PathVariable Long id, @RequestBody Map<String, Object> data) {
        return wishFeignClient.updateCategory(id, data);
    }

    @DeleteMapping("/wish/categories/{id}")
    @OperLog(title = "心愿分类管理", businessType = 3)
    @RequiresPermission("business:wishCategory:remove")
    @Operation(summary = "删除心愿分类", description = "系统预设分类不可删除")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        return wishFeignClient.deleteCategory(id);
    }
}
