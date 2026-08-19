package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理后台互动记录 VO（Sprint 1.2）。
 *
 * <p>含已取消（软删）记录：deletedAt 非空表示已取消，审计需要完整轨迹。</p>
 *
 * @param id            互动记录 ID
 * @param wishId        心愿 ID
 * @param wishTitle     心愿标题（批量关联，避免逐条联查）
 * @param userId        互动用户 ID
 * @param nickname      互动用户昵称（Feign，降级占位）
 * @param type          互动类型
 * @param content       祝福内容（仅 BLESS）
 * @param starlightCost 消耗星光数
 * @param deletedAt     取消时间（null=有效）
 * @param createdAt     互动时间
 */
@Schema(description = "管理后台互动记录")
public record AdminInteractionVO(
        @Schema(description = "互动记录 ID") Long id,
        @Schema(description = "心愿 ID") Long wishId,
        @Schema(description = "心愿标题") String wishTitle,
        @Schema(description = "互动用户 ID") Long userId,
        @Schema(description = "互动用户昵称") String nickname,
        @Schema(description = "互动类型") InteractionType type,
        @Schema(description = "祝福内容") String content,
        @Schema(description = "消耗星光数") Integer starlightCost,
        @Schema(description = "取消时间（null=有效）") LocalDateTime deletedAt,
        @Schema(description = "互动时间") LocalDateTime createdAt
) {
}
