package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminDeptRequest(
    Long parentId,
    String ancestors,
    @NotBlank String deptName,
    @NotNull Integer orderNum,
    String leader,
    String phone,
    String email,
    Integer status
) {}
