package com.cloudmart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "秒杀结果VO")
public record SeckillResultVO(
    @Schema(description = "是否成功") Boolean success,
    @Schema(description = "订单号") String orderNo,
    @Schema(description = "提示信息") String message
) {}
