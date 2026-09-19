package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 好友项（三期）：{@code direction} 区分"好友 / 收到申请 / 我发出的申请"，
 * 前端按它决定按钮（互访 / 同意 / 等待）。
 */
@Schema(description = "宠物好友项")
public record PetFriendVO(
        @Schema(description = "好友用户 ID") Long userId,
        @Schema(description = "好友昵称") String nickname,
        @Schema(description = "好友主宠 ID") Long petId,
        @Schema(description = "好友主宠名") String petName,
        @Schema(description = "宠物种类") String species,
        @Schema(description = "宠物等级") Integer level,
        @Schema(description = "进化阶段") Integer evolutionStage,
        @Schema(description = "皮肤编码") String skinCode,
        @Schema(description = "状态: PENDING/ACTIVE/REJECTED") String status,
        @Schema(description = "方向: ACTIVE/INCOMING/OUTGOING/SELF") String direction,
        @Schema(description = "我访问好友次数") Integer visitCount,
        @Schema(description = "最近一次互访时间") LocalDateTime lastVisitAt,
        @Schema(description = "今日是否已互访过") Boolean visitedToday
) {
}
