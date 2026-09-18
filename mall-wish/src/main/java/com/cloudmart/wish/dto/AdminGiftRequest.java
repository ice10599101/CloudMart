package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 管理端礼物目录新增/编辑请求（全站虚拟礼物）。
 *
 * @param name           礼物名称（1-50 字符）
 * @param iconUrl        礼物图标 URL（可选，空时前端展示默认礼物图标）
 * @param animationUrl   礼物动效资源 URL（可选）
 * @param priceStarlight 星光单价（1-100000）
 * @param status         状态：ON_SHELF / OFF_SHELF
 * @param sort           排序值（越小越靠前）
 * @param description    礼物描述（可选，≤200 字符）
 */
@Schema(description = "管理端礼物目录保存请求")
public record AdminGiftRequest(
        @Schema(description = "礼物名称", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "礼物名称不能为空")
        @Size(max = 50, message = "礼物名称不能超过 50 字符")
        String name,

        @Schema(description = "礼物图标 URL")
        @Size(max = 500, message = "图标 URL 不能超过 500 字符")
        String iconUrl,

        @Schema(description = "礼物动效资源 URL")
        @Size(max = 500, message = "动效 URL 不能超过 500 字符")
        String animationUrl,

        @Schema(description = "星光单价", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "星光单价不能为空")
        @Min(value = 1, message = "星光单价至少为 1")
        @Max(value = 100000, message = "星光单价不能超过 100000")
        Integer priceStarlight,

        @Schema(description = "状态", allowableValues = {"ON_SHELF", "OFF_SHELF"})
        @Pattern(regexp = "ON_SHELF|OFF_SHELF", message = "状态非法")
        String status,

        @Schema(description = "排序值（越小越靠前）")
        @Min(value = 0, message = "排序值不能为负")
        Integer sort,

        @Schema(description = "礼物描述（可选，最多 200 字符）")
        @Size(max = 200, message = "描述不能超过 200 字符")
        String description
) {
}
