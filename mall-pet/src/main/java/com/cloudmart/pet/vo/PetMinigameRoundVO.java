package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 小游戏对局历史项（N04）：实体 {@code PetMinigameRound} 的展示投影。
 *
 * <p>明确不暴露内部字段：userId（归属）、sequence（目标序列 JSON）、ops（操作流水 JSON）、
 * rewardOperationId（奖励结算凭据）、quotaDate（收益配额日期）、createdAt/updatedAt（审计时间）——
 * 前端契约与表结构解耦，后续动表不破坏客户端。</p>
 */
@Schema(description = "小游戏对局历史项")
public record PetMinigameRoundVO(
        @Schema(description = "对局 ID") Long roundId,
        @Schema(description = "游戏类型（N04 定义的游戏编码）") String gameType,
        @Schema(description = "对局状态：ACTIVE=进行中 / SETTLED=已结算 / EXPIRED=已超时") String status,
        @Schema(description = "规则版本号（客户端按此选择玩法逻辑）") String ruleVersion,
        @Schema(description = "开始时间（UTC）") LocalDateTime startedAt,
        @Schema(description = "截止时间（UTC，超时未结算转 EXPIRED）") LocalDateTime deadlineAt,
        @Schema(description = "成功次数") Integer successCount,
        @Schema(description = "是否有收益资格（额度内结算才有）") Boolean rewardEligible
) {
}
