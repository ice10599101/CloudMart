package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotNull;

public record AdminStatusUpdateRequest(
    @NotNull(message = "状态不能为空")
    Integer status
) {}
