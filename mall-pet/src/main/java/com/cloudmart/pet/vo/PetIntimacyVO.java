package com.cloudmart.pet.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 亲密度与陪伴概览（三期）。
 *
 * <p>{@code expBonusPercent} 是服务端算好的展示值（避免前端自行计算暴露公式）；
 * {@code levels} 用于前端画进度阶梯（阈值 + 名称，均来自配置）。</p>
 */
@Schema(description = "宠物亲密度与陪伴")
public record PetIntimacyVO(
        @Schema(description = "当前亲密度点数") Integer intimacy,
        @Schema(description = "亲密度等级（1 起）") Integer level,
        @Schema(description = "亲密度等级名") String levelName,
        @Schema(description = "当前等级起点（点数）") Integer levelFloor,
        @Schema(description = "下一等级阈值（满级为 null）") Integer nextLevelAt,
        @Schema(description = "距下一等级还需点数（满级 0）") Integer toNext,
        @Schema(description = "经验加成百分比（如 3 表示 +3%）") Integer expBonusPercent,
        @Schema(description = "累计陪伴时长（秒）") Long companionSeconds,
        @Schema(description = "今日陪伴时长（秒）") Integer todayCompanionSeconds,
        @Schema(description = "今日陪伴秒数上限") Integer dailyCompanionCapSeconds,
        @Schema(description = "累计陪伴天数") Integer companionDays,
        @Schema(description = "连续陪伴天数") Integer companionStreak,
        @Schema(description = "等级阶梯（阈值 + 名称）") List<LevelItem> levels
) {
    /** 亲密度等级阶梯项 */
    @Schema(description = "亲密度等级阶梯")
    public record LevelItem(
            @Schema(description = "等级（1 起）") Integer level,
            @Schema(description = "等级名") String name,
            @Schema(description = "所需亲密度") Integer threshold,
            @Schema(description = "是否已达成") Boolean achieved
    ) {
    }
}
