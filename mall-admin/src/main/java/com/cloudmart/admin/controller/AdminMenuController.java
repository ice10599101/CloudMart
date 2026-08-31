package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminMenuRequest;
import com.cloudmart.admin.dto.AdminMenuResponse;
import com.cloudmart.admin.service.AdminMenuService;
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
@RequestMapping("/menus")
@Tag(name = "菜单管理", description = "菜单树查询、CRUD、角色菜单查询")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    public AdminMenuController(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    @GetMapping("/tree")
    @Operation(summary = "菜单树", description = "查询所有菜单的树形结构，登录管理员即可访问")
    public ApiResponse<List<AdminMenuResponse>> tree() {
        return ApiResponse.ok(adminMenuService.tree());
    }

    @GetMapping("/role/{roleId}")
    @RequiresPermission("admin:menu:query")
    @Operation(summary = "角色菜单", description = "查询指定角色关联的菜单列表")
    public ApiResponse<List<AdminMenuResponse>> listByRoleId(@PathVariable("roleId") Long roleId) {
        return ApiResponse.ok(adminMenuService.listByRoleId(roleId));
    }

    @PostMapping
    @RequiresPermission("admin:menu:add")
    @OperLog(title = "菜单管理", businessType = 1)
    @Operation(summary = "新增菜单", description = "创建菜单项")
    public ApiResponse<Void> create(@Valid @RequestBody AdminMenuRequest request) {
        adminMenuService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:menu:edit")
    @OperLog(title = "菜单管理", businessType = 2)
    @Operation(summary = "修改菜单", description = "更新菜单信息")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminMenuRequest request) {
        adminMenuService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:menu:remove")
    @OperLog(title = "菜单管理", businessType = 3)
    @Operation(summary = "删除菜单", description = "删除菜单前检查是否有子菜单")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminMenuService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("admin:menu:edit")
    @OperLog(title = "菜单管理", businessType = 2)
    @Operation(summary = "切换状态", description = "启用或禁用菜单")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminMenuService.updateStatus(id, body.get("status"));
        return ApiResponse.ok(null);
    }
}
