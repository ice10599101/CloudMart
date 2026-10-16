package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端更新品牌许愿池请求（name/targetCount/rewardJson/endAt 可变，null 表示不变）。
 *
 * <p>BASE-01：类型化 DTO 取代 Map 透传，数由 Jackson 按声明类型解析。</p>
 *
 * @param name        池名称（null 不变）
 * @param targetCount 目标人数（null 不变；&lt;1 时服务端忽略）
 * @param rewardJson  奖励配置 JSON（null 不变）
 * @param endAt       结束时间（ISO-8601 LocalDateTime 字符串，null 不变）
 */
@Schema(description = "品牌许愿池更新请求")
public record BrandPoolUpdateRequest(
        @Schema(description = "池名称（null 不变）")
        String name,

        @Schema(description = "目标人数（null 不变）")
        Integer targetCount,

        @Schema(description = "奖励配置 JSON（null 不变）")
        String rewardJson,

        @Schema(description = "结束时间（ISO-8601，null 不变）")
        String endAt) {
}
