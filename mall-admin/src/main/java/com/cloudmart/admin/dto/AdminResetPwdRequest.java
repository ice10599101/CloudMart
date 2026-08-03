package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminResetPwdRequest(
    @NotNull Long userId,
    @NotBlank String newPassword
) {}
