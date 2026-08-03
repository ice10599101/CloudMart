package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminUserRoleAssignRequest(
    @NotNull List<Long> roleIds
) {}
