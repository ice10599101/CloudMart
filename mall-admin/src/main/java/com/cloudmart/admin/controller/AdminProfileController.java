package com.cloudmart.admin.controller;

import com.cloudmart.admin.dto.AdminPasswordUpdateRequest;
import com.cloudmart.admin.dto.AdminProfileResponse;
import com.cloudmart.admin.dto.AdminProfileUpdateRequest;
import com.cloudmart.admin.service.AdminProfileService;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.context.AdminSecurityContext;
import com.cloudmart.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/profile")
@Tag(name = "个人信息", description = "管理员个人信息查看与修改")
public class AdminProfileController {

    private final AdminProfileService adminProfileService;

    public AdminProfileController(AdminProfileService adminProfileService) {
        this.adminProfileService = adminProfileService;
    }

    @GetMapping
    @Operation(summary = "获取个人信息", description = "获取当前登录管理员个人信息")
    public ApiResponse<AdminProfileResponse> getProfile() {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        return ApiResponse.ok(adminProfileService.getProfile(ctx.userId()));
    }

    @PutMapping
    @Operation(summary = "修改个人信息", description = "更新当前登录管理员的个人信息")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody AdminProfileUpdateRequest request) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        adminProfileService.updateProfile(ctx.userId(), request.nickname(), request.email(), request.phone(), request.avatar());
        return ApiResponse.ok(null);
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改当前登录管理员密码")
    public ApiResponse<Void> updatePassword(@Valid @RequestBody AdminPasswordUpdateRequest request) {
        AdminSecurityContext ctx = AdminSecurityContext.get();
        if (ctx == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        adminProfileService.updatePassword(ctx.userId(), request.oldPassword(), request.newPassword());
        return ApiResponse.ok(null);
    }
}
