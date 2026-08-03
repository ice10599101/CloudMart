package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminDictDataRequest;
import com.cloudmart.admin.dto.AdminDictDataResponse;
import com.cloudmart.admin.service.AdminDictDataService;
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
@RequestMapping("/dict/data")
@Tag(name = "字典数据管理", description = "字典数据CRUD与缓存刷新")
public class AdminDictDataController {

    private final AdminDictDataService adminDictDataService;

    public AdminDictDataController(AdminDictDataService adminDictDataService) {
        this.adminDictDataService = adminDictDataService;
    }

    @GetMapping("/type/{dictType}")
    @Operation(summary = "根据字典类型查询数据", description = "根据字典类型编码获取字典数据列表，用于前端下拉框")
    public ApiResponse<List<AdminDictDataResponse>> listByType(@PathVariable("dictType") String dictType) {
        return ApiResponse.ok(adminDictDataService.listByType(dictType));
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:dict:query")
    @Operation(summary = "查询字典数据详情", description = "根据ID获取字典数据信息")
    public ApiResponse<AdminDictDataResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminDictDataService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:dict:add")
    @OperLog(title = "字典管理", businessType = 1)
    @Operation(summary = "新增字典数据", description = "创建字典数据")
    public ApiResponse<Void> create(@Valid @RequestBody AdminDictDataRequest request) {
        adminDictDataService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:dict:edit")
    @OperLog(title = "字典管理", businessType = 2)
    @Operation(summary = "修改字典数据", description = "更新字典数据信息")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminDictDataRequest request) {
        adminDictDataService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:dict:remove")
    @OperLog(title = "字典管理", businessType = 3)
    @Operation(summary = "删除字典数据", description = "删除字典数据")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminDictDataService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("system:dict:edit")
    @OperLog(title = "字典管理", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用字典数据")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminDictDataService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
