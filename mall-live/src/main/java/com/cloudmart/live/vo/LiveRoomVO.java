package com.cloudmart.live.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "直播间VO")
public record LiveRoomVO(
    @Schema(description = "直播间ID") Long id,
    @Schema(description = "标题") String title,
    @Schema(description = "主播昵称") String anchorName,
    @Schema(description = "封面图") String coverImage,
    @Schema(description = "状态") String status,
    @Schema(description = "关联商品ID") Long productId,
    @Schema(description = "当前在线人数") Integer viewerCount,
    @Schema(description = "开播时间") LocalDateTime startTime,
    @Schema(description = "结束时间") LocalDateTime endTime
) {}
