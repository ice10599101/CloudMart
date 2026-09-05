package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 晋级单维度进度（文档 6.5 等级规则表驱动）。
 *
 * @param metric     指标键（totalWishes / totalCheckinDays / totalFulfilled / totalHelped）
 * @param label      指标中文名（累计许愿 / 累计打卡 / 累计还愿 / 累计帮助）
 * @param current    当前值
 * @param threshold  目标阈值
 * @param percentage 进度百分比（0-100，已达成为 100）
 */
@Schema(description = "晋级单维度进度")
public record LevelRequirementVO(
        @Schema(description = "指标键", example = "totalWishes") String metric,
        @Schema(description = "指标中文名", example = "累计许愿") String label,
        @Schema(description = "当前值") int current,
        @Schema(description = "目标阈值") int threshold,
        @Schema(description = "进度百分比 0-100") int percentage) {
}
