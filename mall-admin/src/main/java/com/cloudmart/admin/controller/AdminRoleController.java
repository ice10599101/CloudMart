package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminRoleDataScopeRequest;
import com.cloudmart.admin.dto.AdminRoleDeptRequest;
import com.cloudmart.admin.dto.AdminRoleMenuRequest;
import com.cloudmart.admin.dto.AdminRoleRequest;
import com.cloudmart.admin.dto.AdminRoleResponse;
import com.cloudmart.admin.service.AdminRoleService;
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
@RequestMapping("/roles")
@Tag(name = "角色管理", description = "角色CRUD、菜单分配、部门分配")
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    public AdminRoleController(AdminRoleService adminRoleService) {
        this.adminRoleService = adminRoleService;
    }

    @GetMapping
    @RequiresPermission("admin:role:list")
    @Operation(summary = "角色列表", description = "查询所有角色列表")
    public ApiResponse<List<AdminRoleResponse>> list() {
        return ApiResponse.ok(adminRoleService.list());
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:role:query")
    @Operation(summary = "查询角色详情", description = "根据ID获取角色信息")
    public ApiResponse<AdminRoleResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminRoleService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:role:add")
    @OperLog(title = "角色管理", businessType = 1)
    @Operation(summary = "新增角色", description = "创建角色并关联菜单")
    public ApiResponse<Void> create(@Valid @RequestBody AdminRoleRequest request) {
        adminRoleService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:role:edit")
    @OperLog(title = "角色管理", businessType = 2)
    @Operation(summary = "修改角色", description = "更新角色信息及菜单部门关联")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminRoleRequest request) {
        adminRoleService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:role:remove")
    @OperLog(title = "角色管理", businessType = 3)
    @Operation(summary = "删除角色", description = "删除角色前检查是否已分配用户")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminRoleService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/menus")
    @RequiresPermission("admin:role:edit")
    @OperLog(title = "角色管理", businessType = 4)
    @Operation(summary = "分配菜单权限", description = "为角色分配菜单权限")
    public ApiResponse<Void> assignMenus(@Valid @RequestBody AdminRoleMenuRequest request) {
        adminRoleService.assignMenus(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/depts")
    @RequiresPermission("admin:role:edit")
    @OperLog(title = "角色管理", businessType = 4)
    @Operation(summary = "分配部门权限", description = "为角色分配数据权限部门")
    public ApiResponse<Void> assignDepts(@Valid @RequestBody AdminRoleDeptRequest request) {
        adminRoleService.assignDepts(request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{id}/menus")
    @RequiresPermission("admin:role:query")
    @Operation(summary = "获取角色菜单ID列表", description = "查询角色已分配的菜单ID列表")
    public ApiResponse<List<Long>> getRoleMenuIds(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminRoleService.getMenuIdsByRoleId(id));
    }

    @PutMapping("/{id}/data-scope")
    @RequiresPermission("admin:role:edit")
    @OperLog(title = "角色管理", businessType = 2)
    @Operation(summary = "设置数据权限范围", description = "设置角色数据权限范围及关联部门")
    public ApiResponse<Void> updateDataScope(@PathVariable("id") Long id,
                                             @Valid @RequestBody AdminRoleDataScopeRequest request) {
        adminRoleService.updateDataScope(id, request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("system:role:edit")
    @OperLog(title = "角色管理", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用角色")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminRoleService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
