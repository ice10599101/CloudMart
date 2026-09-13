package com.cloudmart.wish.vo;

import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeSeason;
import com.cloudmart.wish.enums.TreeWeather;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 世界树聚合状态 VO（对应文档 2.5 GET /wish/tree）。
 *
 * <p>计数三值（totalFruits/totalBloom/totalLight）走 Redis 缓存
 * TTL 5 分钟（四端读同一缓存保证跨端一致，允许 ≤5 分钟延迟）；
 * environment/season/weather/specialEvent 每次实时读取（单行表主键查询
 * + 天气缓存 + 事件索引查询，环境变化即时反映，不受缓存延迟影响）。</p>
 *
 * <p>计数口径 = 树上实际挂果（与果实列表 GET /tree/fruits 同一容量口径）：
 * 只统计最新的 MAX_TREE_FRUITS（48）颗——visibility=PUBLIC + audit_status=APPROVED
 * + is_visible=1 + status ∈ (ACTIVE/FULFILLING/FULFILLED) + 未软删 + tree_theta 非空，
 * 顶栏数字与 3D 树实际挂果一一对应，不再统计历史累计。</p>
 *
 * <p>Sprint 2.2 扩展：season 改读 state.season（mall-job 每日落库，
 * NULL 时实时计算兜底）；新增 weather（和风天气 5 分钟缓存）与
 * specialEvent（全站同步，null=无活跃事件）。时段 timePhase 按用户本地
 * 时区计算，见 GET /tree-env?tzOffsetMinutes=（树页需时段时另调）。</p>
 *
 * @param totalFruits          树上实际挂果数（容量封顶 48）
 * @param totalBloom           树上绽放果实数（fruit_type=BLOOM，仅统计挂果的 48 颗）
 * @param totalLight           树上挂果的点亮数合计（仅统计挂果的 48 颗）
 * @param environment          当前情绪环境（SUNNY/RAIN/RAINBOW，情绪联动状态机）
 * @param season               当前季节（state.season 落库；NULL 时实时计算）
 * @param weather              当前真实天气（和风天气 API 5 分钟缓存，降级晴天）
 * @param specialEvent         当前活跃特殊事件；null 表示无（全站同步展示）
 * @param environmentUpdatedAt 当前环境触发时间（UTC；表未初始化时为 null）
 */
@Schema(name = "WorldTreeVO", description = "世界树聚合状态")
public record WorldTreeVO(

        @Schema(description = "树上实际挂果数（容量封顶 48）", example = "48")
        long totalFruits,

        @Schema(description = "绽放果实数（fruit_type=BLOOM，不含 SPARK）", example = "88")
        long totalBloom,

        @Schema(description = "树上果实累计点亮数", example = "5200")
        long totalLight,

        @Schema(description = "当前情绪环境（SUNNY/RAIN/RAINBOW）", example = "SUNNY")
        TreeEnvironment environment,

        @Schema(description = "当前季节（SPRING/SUMMER/AUTUMN/WINTER，按 UTC 日期）", example = "SUMMER")
        TreeSeason season,

        @Schema(description = "当前真实天气（SUNNY/CLOUDY/RAIN/SNOW；5 分钟缓存，降级晴天）",
                example = "SUNNY")
        TreeWeather weather,

        @Schema(description = "当前活跃特殊事件（如流星雨全站同步）；null 表示无")
        SpecialEventVO specialEvent,

        @Schema(description = "当前环境触发时间（UTC，ISO 8601）")
        LocalDateTime environmentUpdatedAt
) {
}
