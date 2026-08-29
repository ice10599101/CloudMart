package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 合伙人申请审批请求（Sprint 3.5）。
 */
@Schema(description = "合伙人申请审批请求")
public record ReviewApplicationRequest(
        @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "approved 不能为空")
        Boolean approved
) {
}
