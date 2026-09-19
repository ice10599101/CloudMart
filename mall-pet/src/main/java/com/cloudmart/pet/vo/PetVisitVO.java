package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 串门邻居（原文档 §1.1 宠物串门）：其他用户的公开宠物。
 *
 * <p>{@code visitedToday}=今日已去过（同一邻居每日一次）；{@code ownerNickname}
 * 取不到时由服务端给占位昵称（展示型数据 Fail-Open）。</p>
 */
@Schema(description = "串门邻居宠物")
public record PetVisitVO(
        Long petId,
        String name,
        String species,
        Integer level,
        String growthStage,
        Integer evolutionStage,
        String skinCode,
        Long ownerUserId,
        String ownerNickname,
        @Schema(description = "今日是否已串过门") Boolean visitedToday,
        @Schema(description = "最近一次串门时间（可空）") LocalDateTime lastVisitedAt
) {
}
