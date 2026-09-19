package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 修改外观请求（一期免费；白名单同领养）。
 */
@Schema(description = "修改外观请求")
public record UpdateAppearanceRequest(
        @Schema(description = "外观颜色: orange/gray/white/brown/pink", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择颜色")
        @Pattern(regexp = "orange|gray|white|brown|pink", message = "外观颜色非法")
        String color,

        @Schema(description = "配饰: none/bell/bowtie/glasses/scarf", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择配饰")
        @Pattern(regexp = "none|bell|bowtie|glasses|scarf", message = "配饰非法")
        String accessory
) {
}
