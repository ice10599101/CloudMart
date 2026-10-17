package com.cloudmart.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "拼团成员VO")
public record GroupMemberVO(
    @Schema(description = "记录ID") Long id,
    @Schema(description = "拼团组ID") Long groupOrderId,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "是否团长") Boolean isLeader,
    @Schema(description = "WEB-02：成员关联订单ID（成团建单成功后可查）") Long orderId,
    @Schema(description = "状态") String status,
    @Schema(description = "加入时间") LocalDateTime joinedAt,
    @Schema(description = "WEB-02：成团建单任务状态 SUCCESS/FAILED/PENDING/ABSENT_ADDRESS")
    String orderTaskStatus,
    @Schema(description = "WEB-02：建单失败原因")
    String orderTaskError
) {}
