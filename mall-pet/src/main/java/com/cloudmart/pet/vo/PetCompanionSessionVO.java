package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 陪伴会话与今日累计视图（B05）。
 */
@Schema(description = "陪伴会话视图")
public record PetCompanionSessionVO(
        @Schema(description = "会话 ID（客户端心跳/停止携带）") Long sessionId,
        @Schema(description = "会话状态: ACTIVE/EXPIRED/STOPPED") String status,
        @Schema(description = "服务端当前时间（UTC）") LocalDateTime serverNow,
        @Schema(description = "本次心跳是否被接受（false=会话已失效/重复请求）") boolean accepted,
        @Schema(description = "本次心跳计入的有效秒数（首次心跳建立基准为 0）") int creditedSeconds,
        @Schema(description = "今日有效陪伴秒数（businessZone 业务日）") int todayAcceptedSeconds,
        @Schema(description = "今日已发亲密度积分") int todayGrantedPoints,
        @Schema(description = "今日积分上限") int dailyPointCap,
        @Schema(description = "当前亲密度") int intimacy,
        @Schema(description = "亲密度等级") int intimacyLevel
) {
}
