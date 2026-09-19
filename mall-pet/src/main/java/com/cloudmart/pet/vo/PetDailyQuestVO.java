package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日任务面板（三期）：任务列表 + 全清宝箱状态。
 */
@Schema(description = "宠物每日任务面板")
public record PetDailyQuestVO(
        @Schema(description = "任务日期（UTC）") LocalDate questDate,
        @Schema(description = "任务列表") List<PetDailyQuestItemVO> quests,
        @Schema(description = "已完成任务数（含待领取，不含宝箱）") Integer completedCount,
        @Schema(description = "已领取任务数") Integer claimedCount,
        @Schema(description = "任务总数") Integer totalCount,
        @Schema(description = "全清宝箱是否可领") Boolean chestClaimable,
        @Schema(description = "全清宝箱是否已领") Boolean chestClaimed,
        @Schema(description = "宝箱奖励经验") Integer chestExp,
        @Schema(description = "宝箱奖励星光") Integer chestCurrency
) {
}
