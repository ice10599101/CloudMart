package com.cloudmart.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "拼团成员VO")
public record GroupMemberVO(
    @Schema(description = "记录ID") Long id,
    @Schema(description = "拼团组ID") Long groupOrderId,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "状态") String status,
    @Schema(description = "加入时间") LocalDateTime joinedAt
) {}
