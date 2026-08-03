package com.cloudmart.admin.dto.feign;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建秒杀活动请求，与 mall-seckill 服务端 CreateActivityRequest 字段对齐
 */
public record CreateActivityRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 500) String description,
    @NotNull LocalDateTime startTime,
    @NotNull LocalDateTime endTime
) {}
