package com.cloudmart.job.dto;

import java.time.LocalDateTime;

public record SysJobLogResponse(
    Long id,
    Long jobId,
    String jobName,
    String jobGroup,
    String invokeTarget,
    String cronExpression,
    String jobMessage,
    Integer status,
    String exceptionInfo,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String duration
) {}
