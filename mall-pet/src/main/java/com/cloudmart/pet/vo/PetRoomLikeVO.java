package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 给他人房间点赞的结果（三期，幂等：重复点赞不重复计数）。 */
@Schema(description = "房间点赞结果")
public record PetRoomLikeVO(
        @Schema(description = "房主宠物 ID") Long petId,
        @Schema(description = "累计点赞数") Integer likeCount,
        @Schema(description = "本次是否为新增点赞（false = 之前已点过）") Boolean newlyLiked,
        @Schema(description = "本次获得经验（重复点赞为 0）") Integer rewardExp,
        @Schema(description = "结果文案") String message
) {
}
