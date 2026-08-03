package com.cloudmart.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLevelConfigRequest(
    @NotNull Integer level,
    @NotBlank @Size(max = 30) String title,
    @NotNull Integer minExp,
    String icon,
    String benefits
) {}
