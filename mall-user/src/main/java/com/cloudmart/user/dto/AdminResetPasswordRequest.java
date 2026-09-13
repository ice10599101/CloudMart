package com.cloudmart.user.dto;

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
