package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 宠物关系项（三期）。
 *
 * <p>{@code direction} 区分"我发起的 / 发给我的 / 已建立"，前端按它决定按钮：
 * OUTGOING 显示等待中、INCOMING 显示同意/拒绝、ACTIVE 显示解除。</p>
 */
@Schema(description = "宠物关系项")
public record PetRelationVO(
        @Schema(description = "关系 ID") Long id,
        @Schema(description = "关系类型") String relType,
        @Schema(description = "关系类型中文名") String relTypeLabel,
        @Schema(description = "状态: PENDING/ACTIVE/REJECTED/DISSOLVED") String status,
        @Schema(description = "方向: OUTGOING/INCOMING/ACTIVE") String direction,
        @Schema(description = "关系亲密度") Integer intimacy,
        @Schema(description = "关系亲密度等级（1 起）") Integer intimacyLevel,
        @Schema(description = "关系亲密度等级名") String intimacyLevelName,
        @Schema(description = "距下一等级还需点数") Integer intimacyToNext,
        @Schema(description = "对方宠物 ID") Long petId,
        @Schema(description = "对方宠物名") String petName,
        @Schema(description = "对方宠物种类") String species,
        @Schema(description = "对方宠物等级") Integer level,
        @Schema(description = "对方宠物成长阶段") String growthStage,
        @Schema(description = "对方宠物进化阶段") Integer evolutionStage,
        @Schema(description = "对方宠物皮肤编码") String skinCode,
        @Schema(description = "对方主人昵称（服务降级时为占位）") String ownerNickname,
        @Schema(description = "申请留言") String message,
        @Schema(description = "发起时间") LocalDateTime createdAt,
        @Schema(description = "确认时间") LocalDateTime acceptedAt
) {
}
