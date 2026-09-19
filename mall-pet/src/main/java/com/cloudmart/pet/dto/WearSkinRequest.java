package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 皮肤穿戴请求（皮肤需已在背包，且满足种类/等级/进化阶段要求）。
 */
@Schema(description = "皮肤穿戴请求")
public record WearSkinRequest(
        @Schema(description = "皮肤编码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择要穿戴的皮肤")
        String skinCode
) {
}
