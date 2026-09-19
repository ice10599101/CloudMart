package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 访问他人家园结果（三期）：房间快照 + 本次来访收益（服务端结算，前端只展示）。
 */
@Schema(description = "访问他人家园结果")
public record PetRoomVisitVO(
        @Schema(description = "房主宠物 ID") Long petId,
        @Schema(description = "房主宠物名") String petName,
        @Schema(description = "房主宠物种类") String species,
        @Schema(description = "房主宠物等级") Integer level,
        @Schema(description = "房主宠物进化阶段") Integer evolutionStage,
        @Schema(description = "房主宠物皮肤编码") String skinCode,
        @Schema(description = "房主昵称") String ownerNickname,
        @Schema(description = "欢迎语") String welcomeMessage,
        @Schema(description = "墙纸编码") String wallCode,
        @Schema(description = "地板编码") String floorCode,
        @Schema(description = "舒适度") Integer comfort,
        @Schema(description = "累计来访次数") Integer visitCount,
        @Schema(description = "累计点赞数") Integer likeCount,
        @Schema(description = "是否已点赞") Boolean liked,
        @Schema(description = "今日是否已来访过（重复来访不再给奖励）") Boolean visitedToday,
        @Schema(description = "本次获得心情（重复来访为 0）") Integer rewardHappiness,
        @Schema(description = "本次获得经验（重复来访为 0）") Integer rewardExp,
        @Schema(description = "本次给房主宠物带来的经验") Integer hostRewardExp,
        @Schema(description = "是否好友") Boolean friend,
        @Schema(description = "结果文案（宠物口吻）") String message,
        @Schema(description = "已摆放家具") List<PetHomeItemVO> placed
) {
}
