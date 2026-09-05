package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 等级提升事件（文档 6.5 等级只升不降；前端 L1912 粒子炸裂弹窗 + APP L1917 本地推送依据）。
 *
 * @param previousLevel  提升前等级
 * @param newLevel       提升后等级
 * @param newLevelTitle 提升后等级标题（追梦新人/梦想家/追光者/星火引路人/宇宙守护者）
 */
@Schema(description = "等级提升事件")
public record LevelUpVO(
        @Schema(description = "提升前等级") int previousLevel,
        @Schema(description = "提升后等级") int newLevel,
        @Schema(description = "提升后等级标题") String newLevelTitle) {
}
