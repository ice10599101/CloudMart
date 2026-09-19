package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 对战视图。rounds 为服务端战斗引擎生成的回合流水 JSON，
 * 客户端（Cocos）只负责逐回合播放，可跳过直接看结果。
 */
@Schema(description = "对战记录")
public record PetBattleVO(
        Long battleId,
        String mode,
        String status,
        String role,
        Long attackerPetId,
        String attackerPetName,
        Long attackerUserId,
        Long defenderPetId,
        String defenderPetName,
        Long defenderUserId,
        Long winnerPetId,
        @Schema(description = "回合流水 JSON：[{round,actorPetId,actorName,action,damage,critical,dodged,targetPetId,targetRemainingHp}]") String rounds,
        Integer expReward,
        Integer currencyReward,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {
}
