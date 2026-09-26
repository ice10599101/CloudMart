package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 统一动作可执行性（B06）：客户端按钮禁用/文案依据，服务端权威。
 */
@Schema(description = "互动动作可执行性")
public record PetActionVO(
        @Schema(description = "动作标识: FEED/PLAY/CLEAN/REST/WORK/STUDY/BOTTLE/BATTLE") String action,
        @Schema(description = "当前是否可执行") boolean allowed,
        @Schema(description = "不可执行原因码（额度耗尽/状态已满/忙碌等）") String reasonCode,
        @Schema(description = "不可执行原因文案") String reasonText,
        @Schema(description = "下次可用时间（冷却/活动截止，UTC）") LocalDateTime nextAvailableAt,
        @Schema(description = "今日剩余有收益次数") Integer rewardRemainingToday
) {
}
