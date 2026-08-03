package com.cloudmart.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SysJobRequest(
    @NotBlank @Size(max = 64) String jobName,
    @NotBlank @Size(max = 64) String jobGroup,
    @NotBlank @Size(max = 500) String invokeTarget,
    @NotBlank @Size(max = 255) String cronExpression,
    Integer misfirePolicy,
    Integer concurrent,
    Integer status,
    @Size(max = 500) String remark
) {}
