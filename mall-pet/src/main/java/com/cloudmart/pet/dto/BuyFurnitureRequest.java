package com.cloudmart.pet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 购买家具请求（三期家园）。 */
@Schema(description = "购买家具请求")
public record BuyFurnitureRequest(
        @Schema(description = "家具编码（家园商城返回的 code）", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "请选择家具")
        String furnitureCode
) {
}
