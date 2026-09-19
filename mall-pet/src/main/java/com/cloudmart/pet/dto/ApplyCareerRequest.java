package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 入职/转职请求（三期宠物职业）。 */
@Schema(description = "宠物入职请求")
public record ApplyCareerRequest(
        @Schema(description = "职业编码（职业列表返回的 code）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择职业")
        String careerCode
) {
}
