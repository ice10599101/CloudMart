package com.cloudmart.coupon.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "生成兑换码请求")
public record GenerateExchangeCodeRequest(

    @Schema(description = "优惠券模板ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "模板ID不能为空")
    Long templateId,

    @Schema(description = "生成数量(1-1000)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 1000, message = "单次生成数量不能超过1000")
    Integer quantity
) {}
