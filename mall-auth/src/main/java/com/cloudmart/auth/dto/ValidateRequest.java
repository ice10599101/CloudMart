package com.cloudmart.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidateRequest(
    @NotBlank String account,
    @NotBlank String password
) {}
