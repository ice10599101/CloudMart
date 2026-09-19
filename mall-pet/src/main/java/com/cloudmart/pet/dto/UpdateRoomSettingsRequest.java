package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 家园设置请求（三期）：是否允许来访 + 欢迎语。 */
@Schema(description = "家园设置请求")
public record UpdateRoomSettingsRequest(
        @Schema(description = "是否允许来访（null = 不修改）") Boolean isPublic,
        @Schema(description = "欢迎语（最多 40 字，null = 不修改）")
        @Size(max = 40, message = "欢迎语最多 40 字")
        String welcomeMessage
) {
}
