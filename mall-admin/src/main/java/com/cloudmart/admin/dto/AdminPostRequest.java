package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminPostRequest(
    @NotBlank String postCode,
    @NotBlank String postName,
    @NotNull Integer orderNum,
    Integer status,
    String remark
) {}
