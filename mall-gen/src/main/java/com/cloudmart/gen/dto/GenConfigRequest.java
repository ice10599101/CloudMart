package com.cloudmart.gen.dto;

import jakarta.validation.constraints.NotBlank;

public record GenConfigRequest(
    @NotBlank String tableName,
    String packageName,
    String moduleName,
    String businessName,
    String functionName,
    String tablePrefix
) {}
