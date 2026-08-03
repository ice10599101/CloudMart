package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "秒杀活动DTO")
public record SeckillActivityDTO(

    @Schema(description = "活动ID")
    Long id,

    @Schema(description = "活动名称")
    String name,

    @Schema(description = "活动描述")
    String description,

    @Schema(description = "开始时间")
    LocalDateTime startTime,

    @Schema(description = "结束时间")
    LocalDateTime endTime,

    @Schema(description = "状态")
    String status,

    @Schema(description = "创建时间")
    LocalDateTime createdAt
) {}
