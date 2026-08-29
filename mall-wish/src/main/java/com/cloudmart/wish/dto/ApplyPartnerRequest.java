package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 心愿合伙人申请请求（Sprint 3.5：提交 wishId + skills）。
 */
@Schema(description = "合伙人申请请求")
public record ApplyPartnerRequest(
        @Schema(description = "协作心愿 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "心愿 ID 不能为空")
        Long wishId,

        @Schema(description = "技能标签（可选）")
        List<String> skills
) {
}
