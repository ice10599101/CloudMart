package com.cloudmart.admin.dto;

import java.time.LocalDateTime;

public record AdminOperLogResponse(
    Long id,
    String title,
    Integer businessType,
    String method,
    String requestMethod,
    Integer operatorType,
    Long operUserId,
    String operName,
    String deptName,
    String operUrl,
    String operIp,
    String operLocation,
    String operParam,
    String jsonResult,
    Integer status,
    String errorMsg,
    LocalDateTime operTime,
    Long costTime
) {}
