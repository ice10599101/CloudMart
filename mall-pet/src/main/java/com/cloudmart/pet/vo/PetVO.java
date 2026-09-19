package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 宠物完整视图（懒更新结算后的权威状态）。
 *
 * <p>数值全部由服务端计算：expToNext/今日喂食余量/进行中活动覆盖信息在服务端合成，
 * 客户端（含 Cocos 场景）只做展示与意图发起。</p>
 */
@Schema(description = "我的宠物")
public record PetVO(
        Long petId,
        Long userId,
        String name,
        String species,
        String appearance,
        String personality,
        Integer level,
        Integer exp,
        @Schema(description = "升到下一级所需经验") Integer expToNext,
        String growthStage,
        Integer hp,
        Integer maxHp,
        Integer hunger,
        Integer happiness,
        Integer energy,
        Integer cleanliness,
        Integer strength,
        Integer intelligence,
        Integer agility,
        Integer charm,
        @Schema(description = "状态快照: IDLE/WORKING/STUDYING/FISHING/RESTING") String status,
        @Schema(description = "进行中的活动类型（无则为 null）") String activityType,
        @Schema(description = "进行中活动预计完成时间") LocalDateTime activityFinishedAt,
        @Schema(description = "已完成待领取的活动类型（无则为 null）") String claimableActivityType,
        Boolean isPublic,
        @Schema(description = "今日剩余喂食次数（null 表示限流服务降级为不限）") Integer feedRemainingToday,
        LocalDateTime lastStateUpdateAt
) {
}
