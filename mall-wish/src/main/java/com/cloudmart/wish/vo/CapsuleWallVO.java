package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 公共胶囊墙条目（§6）：匿名展示，不暴露作者身份。
 */
@Schema(description = "公共胶囊墙条目")
public record CapsuleWallVO(
        @Schema(description = "胶囊 ID") Long capsuleId,
        @Schema(description = "标题") String title,
        @Schema(description = "正文（已转义，匿名展示）") String content,
        @Schema(description = "开启时间") LocalDateTime openedAt,
        @Schema(description = "上墙时间") LocalDateTime wallDecidedAt
) {
}
