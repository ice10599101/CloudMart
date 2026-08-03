package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminDictDataRequest(
    @NotBlank String dictType,
    @NotNull Integer dictSort,
    @NotBlank String dictLabel,
    @NotBlank String dictValue,
    String cssClass,
    String listClass,
    Integer isDefault,
    @NotNull Integer status,
    String remark
) {}
