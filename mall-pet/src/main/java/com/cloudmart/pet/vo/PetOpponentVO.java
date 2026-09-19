package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对战候选对手（PvP 真实宠物 + PvE 野生宠物模板混排）。
 */
@Schema(description = "对战候选")
public record PetOpponentVO(
        Long petId,
        String name,
        String species,
        Integer level,
        String growthStage,
        @Schema(description = "是否野生宠物（PvE）") Boolean isWild,
        Long ownerUserId,
        @Schema(description = "主人昵称（Feign 降级时为占位昵称）") String ownerNickname
) {
}
