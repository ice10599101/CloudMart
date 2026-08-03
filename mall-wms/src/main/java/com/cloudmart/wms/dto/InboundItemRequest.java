package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "入库明细请求")
public record InboundItemRequest(
    @Schema(description = "SKU ID") @NotNull Long skuId,
    @Schema(description = "商品名称") @NotBlank String productName,
    @Schema(description = "预期数量") @NotNull Integer expectedQuantity,
    @Schema(description = "库位编码") String locationCode
) {}
