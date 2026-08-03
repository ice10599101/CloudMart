package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminLoginLogResponse(
    Long id,
    String username,
    String ipaddr,
    String loginLocation,
    String browser,
    String os,
    Integer status,
    String msg,
    LocalDateTime loginTime
) {}
