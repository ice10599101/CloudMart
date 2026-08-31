package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminDictTypeRequest;
import com.cloudmart.admin.dto.AdminDictTypeResponse;
import com.cloudmart.admin.service.AdminDictTypeService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dict/types")
@Tag(name = "字典类型管理", description = "字典类型CRUD与缓存刷新")
public class AdminDictTypeController {

    private final AdminDictTypeService adminDictTypeService;

    public AdminDictTypeController(AdminDictTypeService adminDictTypeService) {
        this.adminDictTypeService = adminDictTypeService;
    }

    @GetMapping
    @RequiresPermission("admin:dict:list")
    @Operation(summary = "字典类型列表", description = "查询所有字典类型列表")
    public ApiResponse<List<AdminDictTypeResponse>> list() {
        return ApiResponse.ok(adminDictTypeService.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:dict:query")
    @Operation(summary = "查询字典类型详情", description = "根据ID获取字典类型信息")
    public ApiResponse<AdminDictTypeResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminDictTypeService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:dict:add")
    @OperLog(title = "字典管理", businessType = 1)
    @Operation(summary = "新增字典类型", description = "创建字典类型，类型编码需唯一")
    public ApiResponse<Void> create(@Valid @RequestBody AdminDictTypeRequest request) {
        adminDictTypeService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:dict:edit")
    @OperLog(title = "字典管理", businessType = 2)
    @Operation(summary = "修改字典类型", description = "更新字典类型信息")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminDictTypeRequest request) {
        adminDictTypeService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:dict:remove")
    @OperLog(title = "字典管理", businessType = 3)
    @Operation(summary = "删除字典类型", description = "删除字典类型")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminDictTypeService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/cache/refresh")
    @RequiresPermission("admin:dict:remove")
    @OperLog(title = "字典管理", businessType = 9)
    @Operation(summary = "刷新字典类型缓存", description = "重新加载所有字典类型到Redis缓存")
    public ApiResponse<Void> refreshCache() {
        adminDictTypeService.refreshCache();
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("admin:dict:edit")
    @OperLog(title = "字典管理", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用字典类型")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminDictTypeService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
