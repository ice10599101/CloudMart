package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 排行榜条目（原文档 §80 宠物排行榜；仅公开宠物入榜）。
 */
@Schema(description = "宠物排行榜条目")
public record PetRankingVO(
        Integer rank,
        Long petId,
        String name,
        String species,
        Integer level,
        @Schema(description = "榜单维度数值：等级/胜场/捞瓶数") Long value,
        Long ownerUserId,
        @Schema(description = "主人昵称（Feign 降级时为占位昵称）") String ownerNickname,
        @Schema(description = "是否为我/我的宠物") Boolean isMe
) {
}
