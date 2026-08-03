package com.cloudmart.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginLogRecordRequest(
    @NotBlank String username,
    String ipaddr,
    String loginLocation,
    String browser,
    String os,
    @NotNull Integer status,
    String msg
) {}
