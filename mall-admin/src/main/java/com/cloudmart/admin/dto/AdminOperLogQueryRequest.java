package com.cloudmart.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AdminOperLogQueryRequest(
    String title,
    Integer businessType,
    Integer status,
    String operName,
    String beginTime,
    String endTime,
    @Min(1) Integer page,
    @Min(1) @Max(100) Integer pageSize
) {
    public AdminOperLogQueryRequest {
        if (page == null) page = 1;
        if (pageSize == null) pageSize = 20;
    }
}
