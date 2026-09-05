package com.cloudmart.wish.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 我的等级与晋级进度（文档 L1930：等级查询返回 level + level_title + 距下一级进度）。
 *
 * <p>四端「心愿殿堂/等级进度条」统一数据源：当前等级取 highest_level
 * （只升不降口径），满级（L5）时 nextLevel 及后续字段为 null/空。</p>
 *
 * @param level                 当前等级（1-5）
 * @param levelTitle            等级称号
 * @param totalWishes           累计许愿数
 * @param totalCheckinDays      累计打卡天数
 * @param totalFulfilled        累计还愿数
 * @param totalHelped           累计帮助次数
 * @param nextLevel             下一等级（满级为 null）
 * @param nextLevelTitle        下一等级称号（满级为 null）
 * @param nextLevelRequirements 距下一级各维度进度（满级为空列表）
 */
@Schema(description = "我的等级与晋级进度")
public record MyLevelVO(
        @Schema(description = "当前等级 1-5") int level,
        @Schema(description = "等级称号") String levelTitle,
        @Schema(description = "累计许愿数") int totalWishes,
        @Schema(description = "累计打卡天数") int totalCheckinDays,
        @Schema(description = "累计还愿数") int totalFulfilled,
        @Schema(description = "累计帮助次数") int totalHelped,
        @Schema(description = "下一等级（满级为 null）") Integer nextLevel,
        @Schema(description = "下一等级称号（满级为 null）") String nextLevelTitle,
        @Schema(description = "距下一级各维度进度（满级为空）") List<LevelRequirementVO> nextLevelRequirements) {
}
