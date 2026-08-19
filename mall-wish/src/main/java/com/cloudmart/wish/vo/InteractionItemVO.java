package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 互动列表项 VO（文档 2.2 节）。
 *
 * @param id        互动记录 ID
 * @param userId    互动用户 ID
 * @param nickname  互动用户昵称（Feign 获取，降级为占位值）
 * @param avatar    互动用户头像
 * @param type      互动类型
 * @param content   祝福内容（仅 BLESS 有值）
 * @param createdAt 互动时间
 */
@Schema(description = "互动列表项")
public record InteractionItemVO(
        @Schema(description = "互动记录 ID") Long id,
        @Schema(description = "互动用户 ID") Long userId,
        @Schema(description = "互动用户昵称") String nickname,
        @Schema(description = "互动用户头像") String avatar,
        @Schema(description = "互动类型") InteractionType type,
        @Schema(description = "祝福内容（仅 BLESS）") String content,
        @Schema(description = "互动时间") LocalDateTime createdAt
) {
}
