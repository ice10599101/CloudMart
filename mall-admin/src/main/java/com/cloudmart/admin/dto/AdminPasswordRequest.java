package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminPasswordRequest(
    @NotBlank String oldPassword,
    @NotBlank String newPassword
) {}
