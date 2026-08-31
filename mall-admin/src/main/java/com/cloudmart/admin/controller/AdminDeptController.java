package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminDeptRequest;
import com.cloudmart.admin.dto.AdminDeptResponse;
import com.cloudmart.admin.service.AdminDeptService;
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
@RequestMapping("/depts")
@Tag(name = "部门管理", description = "部门树查询、CRUD")
public class AdminDeptController {

    private final AdminDeptService adminDeptService;

    public AdminDeptController(AdminDeptService adminDeptService) {
        this.adminDeptService = adminDeptService;
    }

    @GetMapping("/tree")
    @RequiresPermission("admin:dept:list")
    @Operation(summary = "部门树", description = "查询所有部门的树形结构")
    public ApiResponse<List<AdminDeptResponse>> tree() {
        return ApiResponse.ok(adminDeptService.tree());
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:dept:query")
    @Operation(summary = "查询部门详情", description = "根据ID获取部门信息")
    public ApiResponse<AdminDeptResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminDeptService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:dept:add")
    @OperLog(title = "部门管理", businessType = 1)
    @Operation(summary = "新增部门", description = "创建部门并自动计算祖级列表")
    public ApiResponse<Void> create(@Valid @RequestBody AdminDeptRequest request) {
        adminDeptService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:dept:edit")
    @OperLog(title = "部门管理", businessType = 2)
    @Operation(summary = "修改部门", description = "更新部门信息，父级变更时重算祖级列表")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminDeptRequest request) {
        adminDeptService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:dept:remove")
    @OperLog(title = "部门管理", businessType = 3)
    @Operation(summary = "删除部门", description = "删除部门前检查是否有子部门或用户")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminDeptService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("admin:dept:edit")
    @OperLog(title = "部门管理", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用部门")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminDeptService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
