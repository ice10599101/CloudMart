package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 世界生命树环境状态 VO（文档 2.2 / Sprint 2.2，四端环境渲染数据源）。
 */
@Data
@AllArgsConstructor
@Schema(description = "世界生命树环境状态")
public class TreeEnvVO {

    @Schema(description = "当前环境：SUNNY/RAIN/RAINBOW（预留季节/天气/特殊事件扩展）",
            example = "RAIN")
    private TreeEnvironment environment;

    @Schema(description = "最近一次环境变更来源（观测用）", example = "MOOD_RAIN")
    private TreeEnvSource source;

    @Schema(description = "当前环境触发时间（ISO 8601）")
    private LocalDateTime triggeredAt;

    @Schema(description = "当前环境过期时间；null 表示持续至下次扫描复评（RAIN 语义）")
    private LocalDateTime expiresAt;

    @Schema(description = "最近一次情绪扫描时间")
    private LocalDateTime lastScanAt;

    @Schema(description = "聚合情绪分数（-1.0~+1.0，10 分钟缓存）；null 表示暂无窗口内样本", example = "-0.65")
    private Double moodScore;

    @Schema(description = "最近一次扫描聚合样本数（仅计数，无情绪明细）", example = "12")
    private Integer sampleCount;
}
