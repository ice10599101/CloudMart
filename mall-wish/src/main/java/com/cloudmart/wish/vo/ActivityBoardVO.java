package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 心愿合伙人组队看板成员（Sprint 3.5：协作进度共享——各自打卡天数 +
 * 最新成长记录互相可见）。
 */
@Schema(description = "合伙人看板成员")
public record ActivityBoardVO(
        Long activityId,
        Long leaderUserId,
        List<MemberBoard> members
) {

    @Schema(description = "看板成员")
    public record MemberBoard(
            Long userId,
            String role,
            String title,
            Integer progressPercentage,
            Integer checkinDays,
            String latestGrowth,
            LocalDateTime latestGrowthAt
    ) {
    }
}
