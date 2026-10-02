package com.cloudmart.seckill.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 秒杀结果（T09）：requestId 为提交时返回的查询凭据，客户端刷新/轮询
 * 继续查同一请求；只有终态失败允许重新发起。
 */
@Schema(description = "秒杀结果DTO")
public record SeckillResultDTO(

    @Schema(description = "秒杀状态: PENDING-排队中, SUCCESS-成功, FAILED-失败")
    String status,

    @Schema(description = "关联订单号（成功时有值）")
    Long orderId,

    @Schema(description = "提示信息")
    String message,

    @Schema(description = "请求ID：提交时生成，凭此查询最终结果")
    String requestId,

    @Schema(description = "结果状态查询地址（网关路径）")
    String statusUrl
) {

    public static SeckillResultDTO of(String status, Long orderId, String message, String requestId) {
        String statusUrl = requestId == null ? null : "/api/seckill/requests/" + requestId;
        return new SeckillResultDTO(status, orderId, message, requestId, statusUrl);
    }
}
