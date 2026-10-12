package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端公共胶囊墙条目（R17）：与公开匿名墙不同，管理视图包含作者 ID、
 * 申请时间与裁决理由，供审核队列与已上墙下架管理使用。
 */
@Schema(description = "管理端胶囊墙条目（含作者与裁决信息）")
public record AdminCapsuleWallVO(
        @Schema(description = "胶囊 ID") Long capsuleId,
        @Schema(description = "作者用户 ID（仅管理端可见）") Long userId,
        @Schema(description = "标题") String title,
        @Schema(description = "正文（已转义）") String content,
        @Schema(description = "开启时间") LocalDateTime openedAt,
        @Schema(description = "申请上墙时间") LocalDateTime wallAppliedAt,
        @Schema(description = "最后裁决时间（通过/驳回/下架）") LocalDateTime wallDecidedAt,
        @Schema(description = "墙状态: 1待审核/2已上墙/3已拒绝/4已撤下") Integer wallStatus,
        @Schema(description = "驳回/下架理由") String wallReason
) {
}
