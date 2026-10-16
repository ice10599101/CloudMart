package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 管理端创建品牌许愿池请求（T23：后台专属，用户端不开放创建）。
 *
 * <p>BASE-01：categoryId 等身份字段由 Jackson 从十进制字符串解析为 Long，
 * 禁止 {@code ((Number) body.get("categoryId")).longValue()} 之类的 Map 强转
 * （前端字符串提交时强转会 ClassCastException）。</p>
 *
 * @param categoryId  分类 ID（缺省 1）
 * @param name        池名称
 * @param targetCount 目标人数（服务端校验 ≥ 1）
 * @param rewardJson  奖励配置 JSON
 * @param endAt       结束时间（ISO-8601 LocalDateTime 字符串，可空）
 */
@Schema(description = "品牌许愿池创建请求")
public record BrandPoolCreateRequest(
        @Schema(description = "分类 ID（缺省 1）")
        Long categoryId,

        @Schema(description = "池名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "池名称不能为空")
        String name,

        @Schema(description = "目标人数（≥1，服务端校验）")
        Integer targetCount,

        @Schema(description = "奖励配置 JSON")
        String rewardJson,

        @Schema(description = "结束时间（ISO-8601，可空）")
        String endAt) {
}
