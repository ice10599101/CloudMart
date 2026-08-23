package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI/提醒策略配置更新请求（管理后台，Sprint 2.5 文档 2.5）。
 *
 * @param configValue 配置值（字符串，业务层按键解析类型）
 */
@Schema(description = "AI/提醒策略配置更新请求")
public record AiConfigUpdateRequest(
        @Schema(description = "配置值", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "配置值不能为空")
        @Size(max = 500, message = "配置值最长 500 字符")
        String configValue
) {
}
