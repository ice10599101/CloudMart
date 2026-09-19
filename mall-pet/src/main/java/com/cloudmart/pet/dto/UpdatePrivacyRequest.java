package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 主页公开开关请求。
 */
@Schema(description = "宠物公开开关请求")
public record UpdatePrivacyRequest(
        @Schema(description = "是否在个人主页公开", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "请指定公开状态")
        Boolean isPublic
) {
}
