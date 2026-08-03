package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminRoleRequest(
    @NotBlank String roleName,
    @NotBlank String roleKey,
    @NotNull Integer roleSort,
    Integer dataScope,
    Integer menuCheckStrictly,
    Integer deptCheckStrictly,
    Integer status,
    String remark,
    List<Long> menuIds
) {}
