package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminRoleDataScopeRequest(
    @NotNull Integer dataScope,
    List<Long> deptIds
) {}
