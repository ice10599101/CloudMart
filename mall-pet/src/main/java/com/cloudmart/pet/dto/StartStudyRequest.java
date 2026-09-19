package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 开始读书请求。
 */
@Schema(description = "开始读书请求")
public record StartStudyRequest(
        @Schema(description = "课程配置 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择课程")
        Long configId
) {
}
