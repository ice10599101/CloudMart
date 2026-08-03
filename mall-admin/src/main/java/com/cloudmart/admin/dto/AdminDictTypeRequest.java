package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminDictTypeRequest(
    @NotBlank String dictName,
    @NotBlank String dictType,
    @NotNull Integer status,
    String remark
) {}
