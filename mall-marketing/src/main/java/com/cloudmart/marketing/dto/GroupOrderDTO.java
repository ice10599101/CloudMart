package com.cloudmart.marketing.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "拼团组DTO")
public record GroupOrderDTO(
    @Schema(description = "拼团组ID") Long id,
    @Schema(description = "拼团活动ID") Long activityId,
    @Schema(description = "团长用户ID") Long leaderUserId,
    @Schema(description = "当前参与人数") Integer currentNumber,
    @Schema(description = "成团所需人数") Integer targetNumber,
    @Schema(description = "状态: PENDING/SUCCESS/FAILED/EXPIRED") String status,
    @Schema(description = "过期时间") LocalDateTime expireTime,
    @Schema(description = "成团时间") LocalDateTime successTime,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "参与成员列表") List<GroupMemberDTO> members
) {}
