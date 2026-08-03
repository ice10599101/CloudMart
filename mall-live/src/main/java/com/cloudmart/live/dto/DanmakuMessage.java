package com.cloudmart.live.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "弹幕消息")
public record DanmakuMessage(
    @Schema(description = "直播间ID") Long roomId,
    @Schema(description = "用户ID") Long userId,
    @Schema(description = "昵称") String nickname,
    @Schema(description = "内容") String content,
    @Schema(description = "时间戳") Long timestamp
) {}
