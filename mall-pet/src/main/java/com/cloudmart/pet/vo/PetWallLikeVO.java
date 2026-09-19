package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/** 留言点赞结果（三期，uk 幂等：重复点赞不重复计数）。 */
@Schema(description = "留言点赞结果")
public record PetWallLikeVO(
        @Schema(description = "留言 ID") Long messageId,
        @Schema(description = "点赞数") Integer likeCount,
        @Schema(description = "我是否已点赞") Boolean liked,
        @Schema(description = "是否本次新增点赞") Boolean newlyLiked,
        @Schema(description = "结果文案") String message
) {
}
