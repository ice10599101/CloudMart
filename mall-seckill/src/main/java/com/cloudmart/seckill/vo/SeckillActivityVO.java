package com.cloudmart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "秒杀活动VO")
public record SeckillActivityVO(
    @Schema(description = "活动ID") Long id,
    @Schema(description = "活动名称") String name,
    @Schema(description = "开始时间") LocalDateTime startTime,
    @Schema(description = "结束时间") LocalDateTime endTime,
    @Schema(description = "状态") String status
) {}
