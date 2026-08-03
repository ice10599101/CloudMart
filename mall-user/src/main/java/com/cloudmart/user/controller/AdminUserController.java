package com.cloudmart.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.api.ApiResponse.Meta;
import com.cloudmart.user.dto.UpdateProfileRequest;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/users")
@Tag(name = "用户管理(后台)", description = "管理后台用户管理接口，仅供内部服务调用")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping("/count")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "会员总数", description = "返回会员总数")
    public ApiResponse<Map<String, Object>> getMemberCount() {
        long count = userService.getMemberCount();
        return ApiResponse.ok(Map.of("count", count));
    }

    @GetMapping
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "用户列表", description = "管理后台分页查询用户列表")
    public ApiResponse<List<UserVO>> listUsers(
            @Parameter(description = "页码", example = "1") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<UserVO> result = userService.listUsers(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询用户详情", description = "管理后台查询用户详情")
    public ApiResponse<UserVO> getUserById(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id) {
        return ApiResponse.ok(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "编辑用户信息", description = "管理后台编辑用户昵称、邮箱等个人资料")
    public ApiResponse<UserVO> updateUser(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(id, request));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "切换用户状态", description = "管理后台启用或禁用用户")
    public ApiResponse<Void> toggleUserStatus(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "状态值，0-禁用 1-启用", example = "1") @RequestParam Integer status) {
        userService.toggleUserStatus(id, status);
        return ApiResponse.ok(null);
    }
}
