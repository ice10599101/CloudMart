package com.cloudmart.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.admin.dto.AdminResetPwdRequest;
import com.cloudmart.admin.dto.AdminStatusUpdateRequest;
import com.cloudmart.admin.dto.AdminUserImportResult;
import com.cloudmart.admin.dto.AdminUserQueryRequest;
import com.cloudmart.admin.dto.AdminUserRoleAssignRequest;
import com.cloudmart.admin.dto.AdminUserRequest;
import com.cloudmart.admin.dto.AdminUserResponse;
import com.cloudmart.admin.dto.AdminUserUpdateRequest;
import com.cloudmart.admin.service.AdminUserService;
import com.cloudmart.common.annotation.OperLog;
import com.cloudmart.common.annotation.RequiresPermission;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "后台用户CRUD、状态变更、密码重置、角色分配、导入导出")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/page")
    @RequiresPermission("admin:user:list")
    @Operation(summary = "分页查询用户", description = "支持按用户名、手机号、状态、部门筛选")
    public ApiResponse<Page<AdminUserResponse>> page(AdminUserQueryRequest request) {
        Page<AdminUserResponse> result = adminUserService.page(request);
        return ApiResponse.ok(result, new Meta(
                request.page(),
                request.pageSize(),
                result.getTotal()
        ));
    }

    @GetMapping("/{id}")
    @RequiresPermission("admin:user:query")
    @Operation(summary = "查询用户详情", description = "根据ID获取用户信息含角色和岗位")
    public ApiResponse<AdminUserResponse> getById(@PathVariable("id") Long id) {
        return ApiResponse.ok(adminUserService.getById(id));
    }

    @PostMapping
    @RequiresPermission("admin:user:add")
    @OperLog(title = "用户管理", businessType = 1)
    @Operation(summary = "新增用户", description = "创建后台用户并关联角色和岗位")
    public ApiResponse<Void> create(@Valid @RequestBody AdminUserRequest request) {
        adminUserService.create(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}")
    @RequiresPermission("admin:user:edit")
    @OperLog(title = "用户管理", businessType = 2)
    @Operation(summary = "修改用户", description = "更新用户信息及角色岗位关联")
    public ApiResponse<Void> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody AdminUserUpdateRequest request) {
        adminUserService.update(id, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("admin:user:remove")
    @OperLog(title = "用户管理", businessType = 3)
    @Operation(summary = "删除用户", description = "逻辑删除用户及关联关系")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        adminUserService.delete(id);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("admin:user:edit")
    @OperLog(title = "用户管理", businessType = 2)
    @Operation(summary = "修改用户状态", description = "启用或禁用用户")
    public ApiResponse<Void> updateStatus(@PathVariable("id") Long id,
                                          @Valid @RequestBody AdminStatusUpdateRequest request) {
        adminUserService.updateStatus(id, request.status());
        return ApiResponse.ok(null);
    }

    @PutMapping("/resetPassword")
    @RequiresPermission("admin:user:resetPwd")
    @OperLog(title = "用户管理", businessType = 2)
    @Operation(summary = "重置密码", description = "管理员重置指定用户密码")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody AdminResetPwdRequest request) {
        adminUserService.resetPassword(request);
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/roles")
    @RequiresPermission("admin:user:edit")
    @OperLog(title = "用户管理", businessType = 2)
    @Operation(summary = "分配角色", description = "为用户分配角色")
    public ApiResponse<Void> assignRoles(@PathVariable("id") Long id,
                                         @Valid @RequestBody AdminUserRoleAssignRequest request) {
        adminUserService.assignRoles(id, request.roleIds());
        return ApiResponse.ok(null);
    }

    @GetMapping("/export")
    @RequiresPermission("admin:user:export")
    @OperLog(title = "用户管理", businessType = 5)
    @Operation(summary = "导出用户Excel", description = "按筛选条件导出用户数据为Excel文件")
    public void exportUsers(AdminUserQueryRequest request, HttpServletResponse response) {
        adminUserService.exportUsers(request, response);
    }

    @PostMapping("/import")
    @RequiresPermission("admin:user:import")
    @OperLog(title = "用户管理", businessType = 6)
    @Operation(summary = "导入用户Excel", description = "从Excel文件批量导入用户数据")
    public ApiResponse<AdminUserImportResult> importUsers(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(adminUserService.importUsers(file));
    }
}
