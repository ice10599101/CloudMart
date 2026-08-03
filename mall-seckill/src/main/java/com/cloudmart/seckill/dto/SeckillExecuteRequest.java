package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "秒杀执行请求")
public record SeckillExecuteRequest(

    @NotNull @Schema(description = "活动ID")
    Long activityId,

    @NotNull @Schema(description = "秒杀商品ID")
    Long seckillProductId
) {}
