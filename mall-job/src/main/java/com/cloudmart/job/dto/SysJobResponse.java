package com.cloudmart.job.dto;

import java.time.LocalDateTime;

public record SysJobResponse(
    Long id,
    String jobName,
    String jobGroup,
    String invokeTarget,
    String cronExpression,
    Integer misfirePolicy,
    Integer concurrent,
    Integer status,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime nextValidTime
) {}
