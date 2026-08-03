package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "秒杀结果DTO")
public record SeckillResultDTO(

    @Schema(description = "秒杀状态: PENDING-排队中, SUCCESS-成功, FAILED-失败")
    String status,

    @Schema(description = "关联订单号（成功时有值）")
    Long orderId,

    @Schema(description = "提示信息")
    String message
) {}
