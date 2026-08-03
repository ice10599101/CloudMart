package com.cloudmart.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "TCC库存扣减请求")
public record TccDeductRequest(
    @Schema(description = "SKU ID") @NotNull Long skuId,
    @Schema(description = "商品ID") @NotNull Long productId,
    @Schema(description = "扣减数量") @NotNull @Min(1) Integer quantity,
    @Schema(description = "关联订单ID") Long orderId
) {}
