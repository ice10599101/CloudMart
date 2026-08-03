package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminConfigRequest(
    @NotBlank String configName,
    @NotBlank String configKey,
    @NotBlank String configValue,
    @NotNull Integer configType,
    String remark
) {}
