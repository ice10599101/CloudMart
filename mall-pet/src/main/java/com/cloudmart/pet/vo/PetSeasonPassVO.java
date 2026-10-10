package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 赛季通行证视图（§6）：当前赛季 + 经验 + 10 档奖励阶梯。
 */
@Schema(description = "赛季通行证")
public record PetSeasonPassVO(
        @Schema(description = "赛季 ID（无进行中赛季为 null）") Long seasonId,
        @Schema(description = "赛季名称") String seasonName,
        @Schema(description = "当前通行证经验") int passExp,
        @Schema(description = "已领取档位") List<Integer> claimedTiers,
        @Schema(description = "档位阶梯") List<Tier> tiers
) {

    @Schema(description = "通行证档位")
    public record Tier(
            @Schema(description = "档位（1..10）") int tier,
            @Schema(description = "所需通行证经验") int requiredExp,
            @Schema(description = "是否已达标") boolean reached,
            @Schema(description = "是否已领取") boolean claimed,
            @Schema(description = "奖励描述") String rewardDesc
    ) {
    }
}
