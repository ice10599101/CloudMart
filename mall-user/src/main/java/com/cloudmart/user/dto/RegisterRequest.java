package com.cloudmart.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Size(min = 6, max = 100) String password,
    @NotBlank @Email @Size(max = 100) String email,
    @NotBlank @Size(min = 1, max = 20) String nickname,
    // 注册邮箱验证码：6 位数字，先经 /users/register/code 下发
    @NotBlank @Pattern(regexp = "\\d{6}", message = "验证码须为 6 位数字") String code
) {}
