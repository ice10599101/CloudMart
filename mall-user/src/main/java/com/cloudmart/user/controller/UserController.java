package com.cloudmart.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.user.dto.*;
import com.cloudmart.user.service.UserService;
import com.cloudmart.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@Tag(name = "用户管理", description = "用户注册、资料、昵称修改等接口")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户，小答号自动生成，邮箱和昵称需唯一")
    public ApiResponse<UserVO> register(
            @Parameter(description = "注册请求体") @Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(userService.register(request));
    }

    @PostMapping("/validate")
    @Operation(summary = "验证用户凭据", description = "通过小答号或邮箱验证用户名密码，供认证服务调用")
    public ApiResponse<UserDTO> validateUser(
            @Parameter(description = "验证请求体") @Valid @RequestBody ValidateRequest request) {
        return ApiResponse.ok(userService.validateUser(request));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public ApiResponse<UserVO> getCurrentUser() {
        Long userId = getCurrentUserId();
        return ApiResponse.ok(userService.getUserById(userId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户信息")
    public ApiResponse<UserVO> getUserById(
            @Parameter(description = "用户ID") @PathVariable Long id) {
        return ApiResponse.ok(userService.getUserById(id));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索用户")
    public ApiResponse<List<UserVO>> searchUsers(
            @Parameter(description = "关键词") @RequestParam String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(userService.searchUsers(keyword, page, pageSize));
    }

    @GetMapping("/batch")
    @Operation(summary = "批量获取用户信息")
    public ApiResponse<List<UserVO>> batchGetUsers(
            @Parameter(description = "用户ID列表") @RequestParam List<Long> ids) {
        return ApiResponse.ok(userService.batchGetUsers(ids));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新用户资料")
    public ApiResponse<UserVO> updateProfile(
            @Parameter(description = "更新资料请求体") @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.ok(userService.updateProfile(userId, request));
    }

    @PutMapping("/nickname")
    @Operation(summary = "修改昵称", description = "每7天可修改一次，昵称不能重复")
    public ApiResponse<UserVO> changeNickname(
            @Parameter(description = "修改昵称请求体") @Valid @RequestBody ChangeNicknameRequest request) {
        Long userId = getCurrentUserId();
        return ApiResponse.ok(userService.changeNickname(userId, request));
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码")
    public ApiResponse<Void> changePassword(
            @Parameter(description = "修改密码请求体") @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = getCurrentUserId();
        userService.changePassword(userId, request);
        return ApiResponse.ok(null);
    }

    @GetMapping("/recommend")
    @Operation(summary = "推荐用户列表")
    public ApiResponse<List<UserVO>> recommendUsers(
            @Parameter(description = "数量") @RequestParam(defaultValue = "6") int limit) {
        Page<UserVO> page = userService.listUsers(1, limit);
        return ApiResponse.ok(page.getRecords());
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询用户")
    public ApiResponse<Page<UserVO>> listUsers(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.listUsers(page, size));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换用户状态")
    public ApiResponse<Void> toggleUserStatus(
            @Parameter(description = "用户ID") @PathVariable Long id,
            @Parameter(description = "状态") @RequestParam Integer status) {
        userService.toggleUserStatus(id, status);
        return ApiResponse.ok(null);
    }

    @GetMapping("/count")
    @Operation(summary = "获取用户总数")
    public ApiResponse<Long> getMemberCount() {
        return ApiResponse.ok(userService.getMemberCount());
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException("UNAUTHORIZED", "未登录或登录已过期");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String principalStr) {
            try {
                return Long.parseLong(principalStr);
            } catch (NumberFormatException e) {
                throw new BusinessException("UNAUTHORIZED", "内部服务调用缺少用户标识");
            }
        }
        throw new BusinessException("UNAUTHORIZED", "无法获取用户信息");
    }
}
