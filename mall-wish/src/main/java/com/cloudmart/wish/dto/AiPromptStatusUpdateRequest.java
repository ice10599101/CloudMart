package com.cloudmart.wish.dto;

import com.cloudmart.wish.enums.AiPromptStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Prompt 模板状态更新请求（管理端，Sprint 2.5）。
 *
 * <p>仅允许状态流转与流量调整，正文不可改（版本不可变原则，
 * 修改正文须创建新版本）。</p>
 *
 * @param status          目标状态（ACTIVE/ARCHIVED）
 * @param trafficPercent  流量百分比（激活/调整分流时使用，可空）
 * @param remark          变更说明（可空）
 */
@Schema(description = "Prompt 模板状态更新请求")
public record AiPromptStatusUpdateRequest(
        @Schema(description = "目标状态：ACTIVE/ARCHIVED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "目标状态不能为空")
        AiPromptStatus status,

        @Schema(description = "流量百分比（1-100，激活或调整分流时使用）")
        Integer trafficPercent,

        @Schema(description = "变更说明")
        @Size(max = 255, message = "备注最长 255 字")
        String remark
) {
}
