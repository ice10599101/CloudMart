package com.cloudmart.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @NotBlank @Size(min = 6, max = 128) String oldPassword,
    @NotBlank @Size(min = 6, max = 128) String newPassword
) {}
