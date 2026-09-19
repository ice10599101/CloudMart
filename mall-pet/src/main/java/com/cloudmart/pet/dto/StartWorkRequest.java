package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 开始打工请求。
 */
@Schema(description = "开始打工请求")
public record StartWorkRequest(
        @Schema(description = "岗位配置 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请选择岗位")
        Long configId
) {
}
