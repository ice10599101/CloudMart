package com.cloudmart.wish.vo;

import com.cloudmart.wish.entity.WishAiConfig;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI/提醒策略配置 VO（管理后台提醒策略页，Sprint 2.5）。
 *
 * @param configKey   配置键
 * @param configValue 配置值
 * @param description 配置说明
 * @param updatedAt   最后更新时间（UTC）
 */
@Schema(description = "AI/提醒策略配置项")
public record AiConfigVO(
        @Schema(description = "配置键") String configKey,
        @Schema(description = "配置值") String configValue,
        @Schema(description = "配置说明") String description,
        @Schema(description = "最后更新时间（UTC）") LocalDateTime updatedAt
) {
    public static AiConfigVO from(WishAiConfig config) {
        return new AiConfigVO(config.getConfigKey(), config.getConfigValue(),
                config.getDescription(), config.getUpdatedAt());
    }
}
