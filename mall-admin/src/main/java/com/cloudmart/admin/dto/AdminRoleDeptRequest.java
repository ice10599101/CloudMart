package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminRoleDeptRequest(
    @NotNull Long roleId,
    List<Long> deptIds
) {}
