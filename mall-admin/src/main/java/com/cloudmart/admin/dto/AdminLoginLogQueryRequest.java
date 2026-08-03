package com.cloudmart.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminLoginLogQueryRequest(
    String username,
    String ipaddr,
    Integer status,
    String beginTime,
    String endTime,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public AdminLoginLogQueryRequest {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 20;
    }
}
