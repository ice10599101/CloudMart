# -*- coding: utf-8 -*-
import io

# ---------- mall-user: AdminResetPasswordRequest DTO ----------
dto = """package com.cloudmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员重置用户密码请求（无需原密码）
 */
public record AdminResetPasswordRequest(
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码至少 6 位")
    String newPassword
) {}
"""
io.open('mall-user/src/main/java/com/cloudmart/user/dto/AdminResetPasswordRequest.java', 'w', encoding='utf-8', newline='\n').write(dto)
print("OK AdminResetPasswordRequest")

# ---------- mall-user: AdminUserController ----------
p = 'mall-user/src/main/java/com/cloudmart/user/controller/AdminUserController.java'
s = io.open(p, encoding='utf-8').read()

old = """    public ApiResponse<List<UserVO>> listUsers(
            @Parameter(description = "页码", example = "1") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<UserVO> result = userService.listUsers(page, size);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }"""
assert old in s
s = s.replace(old, """    public ApiResponse<List<UserVO>> listUsers(
            @Parameter(description = "页码", example = "1") @RequestParam(value = "page", defaultValue = "1") int page,
            @Parameter(description = "每页数量", example = "20") @RequestParam(value = "size", defaultValue = "20") int size,
            @Parameter(description = "小答号模糊筛选") @RequestParam(value = "username", required = false) String username,
            @Parameter(description = "昵称模糊筛选") @RequestParam(value = "nickname", required = false) String nickname,
            @Parameter(description = "状态筛选: 0-禁用, 1-正常") @RequestParam(value = "status", required = false) Integer status) {
        Page<UserVO> result = userService.listUsers(page, size, username, nickname, status);
        return ApiResponse.ok(result.getRecords(), new Meta(page, size, result.getTotal()));
    }""")

old = """    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "编辑用户信息", description = "管理后台编辑用户昵称、邮箱等个人资料")
    public ApiResponse<UserVO> updateUser(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.updateProfile(id, request));"""
assert old in s
s = s.replace(old, """    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "编辑用户信息", description = "管理后台全字段编辑用户资料（昵称/邮箱唯一性排除自身，不受昵称冷却限制）")
    public ApiResponse<UserVO> updateUser(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok(userService.adminUpdateUser(id, request));""")

# 重置密码端点：插在 updateUser 端点之后（toggleUserStatus 之前）
old = """    @PutMapping("/{id}/status")"""
assert old in s
s = s.replace(old, """    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "重置用户密码", description = "管理后台直接设置用户新密码（无需原密码）")
    public ApiResponse<Void> resetPassword(
            @Parameter(description = "用户ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.adminResetPassword(id, request.newPassword());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")""", 1)

# import AdminResetPasswordRequest（dto 包通配？检查）
if "import com.cloudmart.user.dto.*;" not in s and "import com.cloudmart.user.dto.AdminResetPasswordRequest;" not in s:
    s = s.replace("import com.cloudmart.user.dto.UserVO;", "import com.cloudmart.user.dto.AdminResetPasswordRequest;\nimport com.cloudmart.user.dto.UserVO;")
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print("OK AdminUserController")

# 检查 import 风格
s = io.open(p, encoding='utf-8').read()
for line in s.splitlines():
    if line.startswith("import com.cloudmart.user.dto"):
        print("  ", line)
PYEOF