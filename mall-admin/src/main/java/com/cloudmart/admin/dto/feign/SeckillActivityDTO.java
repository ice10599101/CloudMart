package com.cloudmart.admin.dto.feign;

import java.time.LocalDateTime;

/**
 * 秒杀活动 Feign 传输对象，与 mall-seckill 服务端 SeckillActivityDTO 字段对齐
 */
public record SeckillActivityDTO(
    Long id,
    String name,
    String description,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String status,
    LocalDateTime createdAt
) {}
