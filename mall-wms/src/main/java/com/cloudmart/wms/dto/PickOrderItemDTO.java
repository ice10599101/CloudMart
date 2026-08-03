package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "拣货明细DTO")
public record PickOrderItemDTO(
    @Schema(description = "明细ID") Long id,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "SKU属性") String skuAttributes,
    @Schema(description = "数量") Integer quantity,
    @Schema(description = "库位编码") String locationCode,
    @Schema(description = "已拣数量") Integer pickedQuantity
) {}
