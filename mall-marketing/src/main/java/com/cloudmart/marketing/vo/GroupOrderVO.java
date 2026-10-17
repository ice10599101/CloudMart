package com.cloudmart.marketing.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "拼团组VO")
public record GroupOrderVO(
    @Schema(description = "拼团组ID") Long id,
    @Schema(description = "活动ID") Long activityId,
    @Schema(description = "团长用户ID") Long leaderUserId,
    @Schema(description = "当前参与人数") Integer currentNumber,
    @Schema(description = "成团所需人数") Integer targetNumber,
    @Schema(description = "状态") String status,
    @Schema(description = "过期时间") LocalDateTime expireTime,
    @Schema(description = "成团时间") LocalDateTime successTime,
    @Schema(description = "WEB-02：参与成员（含本人建单结果 orderTaskStatus——FAILED/ABSENT_ADDRESS 为补偿状态）")
    List<GroupMemberVO> members
) {}
