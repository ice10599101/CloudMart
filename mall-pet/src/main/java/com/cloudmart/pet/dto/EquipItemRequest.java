package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 装备请求（物品需已在背包；同一部位自动卸下旧装备，服务端保证互斥）。
 */
@Schema(description = "装备穿戴请求")
public record EquipItemRequest(
        @Schema(description = "装备编码", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择要穿戴的装备")
        String itemCode
) {
}
