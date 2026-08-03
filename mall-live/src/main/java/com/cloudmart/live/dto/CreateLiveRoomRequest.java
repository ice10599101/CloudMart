package com.cloudmart.live.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建直播间请求")
public record CreateLiveRoomRequest(
    @Schema(description = "直播间标题") @NotBlank String title,
    @Schema(description = "直播间描述") String description,
    @Schema(description = "主播用户ID") @NotNull Long anchorUserId,
    @Schema(description = "主播昵称") @NotBlank String anchorName,
    @Schema(description = "封面图URL") String coverImage,
    @Schema(description = "直播流地址") String streamUrl,
    @Schema(description = "关联商品ID") Long productId,
    @Schema(description = "关联秒杀活动ID") Long seckillActivityId,
    @Schema(description = "最大在线人数，0=不限") Integer maxViewers
) {}
