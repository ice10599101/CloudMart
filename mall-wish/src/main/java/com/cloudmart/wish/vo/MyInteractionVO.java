package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.InteractionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 我的互动状态 VO（Sprint 1.2 前端按钮状态依据）。
 *
 * <p>用途：心愿详情页判断各互动按钮的初始状态（已同求高亮/今日已祝福禁用），
 * 并提供 interactionId 供取消互动。LIGHT 可重复互动无需状态判定，
 * 仅返回历史记录供展示累计次数。</p>
 *
 * @param id            互动记录 ID（取消互动用）
 * @param type          互动类型
 * @param content       祝福内容（仅 BLESS，已转义；其他类型为 null）
 * @param createdAt     互动时间
 * @param createdToday  是否今日创建（按用户时区判定；BLESS 每愿望每日 1 次的禁用依据）
 */
@Schema(description = "我的互动状态")
public record MyInteractionVO(
        @Schema(description = "互动记录 ID") Long id,
        @Schema(description = "互动类型") InteractionType type,
        @Schema(description = "祝福内容（仅 BLESS）") String content,
        @Schema(description = "互动时间") LocalDateTime createdAt,
        @Schema(description = "是否今日创建（用户时区）") boolean createdToday
) {
}
