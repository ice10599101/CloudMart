package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.enums.TreeTimePhase;
import com.cloudmart.wish.enums.TreeWeather;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 世界生命树环境状态 VO（文档 2.2 / Sprint 2.2，四端环境渲染数据源）。
 *
 * <p>Sprint 2.2 扩展多维环境模型：情绪环境（environment，Sprint 1.5 状态机）/
 * 季节（season，落库）/ 真实天气（weather，和风天气 API）/ 时段
 * （timePhase，按客户端时区偏移计算）/ 特殊事件（specialEvent，管理员触发）。
 * {@code displayEnv} 为聚合展示环境 code（优先级：特殊事件 &gt; 情绪
 * RAINBOW/RAIN &gt; 真实天气），四端可按 displayEnv 取 wish_env_config
 * 渲染配置；season/timePhase 始终独立返回供分层渲染（树冠随季节、
 * 天空底色随时段）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "世界生命树环境状态")
public class TreeEnvVO {

    @Schema(description = "当前情绪环境：SUNNY/RAIN/RAINBOW（树洞情绪联动状态机）",
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

    @Schema(description = "当前季节（SPRING/SUMMER/AUTUMN/WINTER；mall-job 每日 00:00 落库，未扫描时实时计算）",
            example = "SUMMER")
    private TreeSeason season;

    @Schema(description = "当前真实天气（SUNNY/CLOUDY/RAIN/SNOW；和风天气 API 5 分钟缓存，降级晴天）",
            example = "SUNNY")
    private TreeWeather weather;

    @Schema(description = "当前时段（DAY/DUSK/NIGHT/LATE_NIGHT；按客户端时区偏移计算）", example = "NIGHT")
    private TreeTimePhase timePhase;

    @Schema(description = "当前活跃特殊事件；null 表示无（如流星雨全站同步）")
    private SpecialEventVO specialEvent;

    @Schema(description = "聚合展示环境 code（优先级：特殊事件 > 情绪 RAINBOW/RAIN > 真实天气；"
            + "四端按此取环境配置渲染）", example = "METEOR_SHOWER")
    private String displayEnv;
}
