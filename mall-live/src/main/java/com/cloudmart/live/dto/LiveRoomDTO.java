package com.cloudmart.live.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "直播间DTO")
public record LiveRoomDTO(
    @Schema(description = "直播间ID") Long id,
    @Schema(description = "标题") String title,
    @Schema(description = "描述") String description,
    @Schema(description = "主播ID") Long anchorUserId,
    @Schema(description = "主播昵称") String anchorName,
    @Schema(description = "封面图") String coverImage,
    @Schema(description = "直播流地址") String streamUrl,
    @Schema(description = "关联商品ID") Long productId,
    @Schema(description = "关联秒杀活动ID") Long seckillActivityId,
    @Schema(description = "最大在线人数") Integer maxViewers,
    @Schema(description = "当前在线人数") Integer currentViewers,
    @Schema(description = "累计观看人数") Long totalViewers,
    @Schema(description = "状态") String status,
    @Schema(description = "开播时间") LocalDateTime startTime,
    @Schema(description = "结束时间") LocalDateTime endTime,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
