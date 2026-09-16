package com.cloudmart.wish.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 漂流瓶匿名回应请求：type=BLESS 匿名祝福 / LIGHT 点亮对方心愿。
 */
@Schema(description = "漂流瓶匿名回应请求")
public record DriftBottleInteractRequest(
        @Schema(description = "回应类型：BLESS 匿名祝福 / LIGHT 点亮对方心愿", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "回应类型不能为空")
        String type
) {
}