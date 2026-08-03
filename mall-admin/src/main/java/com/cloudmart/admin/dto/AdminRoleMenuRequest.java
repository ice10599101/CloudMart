package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AdminRoleMenuRequest(
    @NotNull Long roleId,
    List<Long> menuIds
) {}
