package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "拼团成员DTO")
public record GroupMemberDTO(
    @Schema(description = "记录ID") Long id,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "拼团组ID") Long groupOrderId,
    @Schema(description = "是否团长") Boolean isLeader,
    @Schema(description = "关联订单ID") Long orderId,
    @Schema(description = "状态: JOINED/CONFIRMED/REFUNDED") String status,
    @Schema(description = "加入时间") LocalDateTime joinedAt
) {}
