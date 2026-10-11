package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 协作 PVE 副本视图（§6/R03）。
 */
@Schema(description = "协作 PVE 副本")
public record PetPveRunVO(
        @Schema(description = "副本 ID") Long runId,
        @Schema(description = "Boss 编码") String bossCode,
        @Schema(description = "Boss 名称") String bossName,
        @Schema(description = "Boss 最大 HP") int bossMaxHp,
        @Schema(description = "Boss 当前 HP") int bossHp,
        @Schema(description = "发起人用户 ID") Long initiatorUserId,
        @Schema(description = "发起人宠物名") String initiatorPetName,
        @Schema(description = "队友用户 ID（未加入为 null）") Long partnerUserId,
        @Schema(description = "队友宠物名") String partnerPetName,
        @Schema(description = "发起人宠物当前 HP") int initiatorPetHp,
        @Schema(description = "队友宠物当前 HP（未加入为 -1）") int partnerPetHp,
        @Schema(description = "状态：OPEN/FIGHTING/WON/FAILED/EXPIRED/CANCELLED") String status,
        @Schema(description = "奖励已发（兼容字段，rewardStatus=SETTLED 时 true）") boolean rewardGranted,
        @Schema(description = "回合流水（最近 N 条）") List<RoundLog> recentRounds,
        @Schema(description = "创建时间") LocalDateTime createdAt,
        @Schema(description = "结束时间") LocalDateTime finishedAt,
        // ---------------- R03 状态机字段 ----------------
        @Schema(description = "已完成回合数") int turnNo,
        @Schema(description = "当前行动方用户 ID") Long nextActorUserId,
        @Schema(description = "当前请求者是否可行动（canAttack）") boolean canAttack,
        @Schema(description = "不可行动原因（NOT_YOUR_TURN/NOT_FIGHTING/RUN_OVER 等）") String blockedReason,
        @Schema(description = "本回合行动截止") LocalDateTime turnDeadlineAt,
        @Schema(description = "副本总时限") LocalDateTime runExpiresAt,
        @Schema(description = "奖励聚合状态：NONE/PENDING/SETTLED") String rewardStatus,
        @Schema(description = "发起人宠物当前 HP/最大 HP") int initiatorPetMaxHp,
        @Schema(description = "队友宠物最大 HP") int partnerPetMaxHp
) {

    @Schema(description = "回合流水条目（对齐 PetBattleEngine.Round 语义 + 攻击方标注）")
    public record RoundLog(
            int round,
            String actorName,
            String action,
            int damage,
            boolean critical,
            boolean dodged,
            String targetName,
            int targetRemainingHp
    ) {
    }
}
