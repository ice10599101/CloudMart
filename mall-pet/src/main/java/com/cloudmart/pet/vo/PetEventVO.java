package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 社区宠物活动（原文档 §89 社区宠物活动）。
 *
 * <p>{@code progress} 为惰性统计出的已完成次数（含本次读取的实时统计），
 * {@code claimable} = 已完成且未领奖；{@code expired} 表示活动窗口已结束且未完成。</p>
 */
@Schema(description = "社区宠物活动")
public record PetEventVO(
        String code,
        String name,
        String description,
        @Schema(description = "统计口径: BOTTLE/BATTLE/WORK/STUDY/FEED/PLAY/VISIT") String eventType,
        Integer targetValue,
        Integer progress,
        Boolean completed,
        Boolean claimable,
        Boolean claimed,
        Boolean expired,
        Integer rewardStarlight,
        Integer rewardExp,
        @Schema(description = "额外奖励物品编码（可空）") String rewardItemCode,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime claimedAt
) {
}
