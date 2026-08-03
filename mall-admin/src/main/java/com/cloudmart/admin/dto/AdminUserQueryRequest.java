package com.cloudmart.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminUserQueryRequest(
    String username,
    String phone,
    Integer status,
    Long deptId,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public AdminUserQueryRequest {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 20;
    }
}
