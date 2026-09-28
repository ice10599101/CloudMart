package com.cloudmart.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册验证码发送请求：匿名可调用，仅收邮箱；频控由服务层 + 网关限流承担。
 */
public record RegisterCodeRequest(
    @NotBlank @Email @Size(max = 100) String email
) {}
