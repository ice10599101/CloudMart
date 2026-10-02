package com.cloudmart.seckill.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 秒杀结果 VO（T09）：requestId 为查询凭据，statusUrl 指向按请求查询端点；
 * success/orderNo/message 保留供存量前端兼容。
 */
@Schema(description = "秒杀结果VO")
public record SeckillResultVO(
    @Schema(description = "请求ID：提交时生成，凭此查询最终结果") String requestId,
    @Schema(description = "秒杀状态: PENDING-排队中, SUCCESS-成功, FAILED-失败") String status,
    @Schema(description = "是否成功") Boolean success,
    @Schema(description = "订单号（成功时有值）") String orderNo,
    @Schema(description = "提示信息") String message,
    @Schema(description = "结果状态查询地址（网关路径）") String statusUrl
) {}
