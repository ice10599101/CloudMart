package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 串门结果（宠物口吻文案由服务端生成，前端直出并驱动 Cocos 动画）。
 */
@Schema(description = "串门结果")
public record PetVisitResultVO(
        @Schema(description = "邻居宠物名") String neighborName,
        @Schema(description = "邻居主人昵称") String ownerNickname,
        @Schema(description = "获得心情") Integer happinessGain,
        @Schema(description = "获得经验") Integer expGain,
        @Schema(description = "宠物口吻文案") String message,
        @Schema(description = "返回主宠最新状态") PetVO pet
) {
}
