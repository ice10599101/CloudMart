package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminRoleResponse(
    Long id,
    String roleName,
    String roleKey,
    Integer roleSort,
    Integer dataScope,
    Integer menuCheckStrictly,
    Integer deptCheckStrictly,
    Integer status,
    String remark,
    LocalDateTime createdAt
) {}
