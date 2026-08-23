package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.AiPromptScene;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Prompt 模板创建请求（管理端，Sprint 2.5）。
 *
 * @param scene           AI 场景
 * @param name            模板名称
 * @param content         Prompt 正文
 * @param abGroup         A/B 分组（ALL/A/B，默认 ALL）
 * @param trafficPercent  流量百分比（1-100，A/B 分流权重）
 * @param remark          版本变更说明
 */
@Schema(description = "Prompt 模板创建请求")
public record AiPromptCreateRequest(
        @Schema(description = "AI 场景", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "场景不能为空")
        AiPromptScene scene,

        @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "模板名称不能为空")
        @Size(max = 100, message = "模板名称最长 100 字")
        String name,

        @Schema(description = "Prompt 正文", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Prompt 正文不能为空")
        String content,

        @Schema(description = "A/B 分组：ALL/A/B", defaultValue = "ALL")
        String abGroup,

        @Schema(description = "流量百分比（1-100）", defaultValue = "100")
        Integer trafficPercent,

        @Schema(description = "版本变更说明")
        @Size(max = 255, message = "备注最长 255 字")
        String remark
) {
}
