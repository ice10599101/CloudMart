package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "入库明细DTO")
public record InboundOrderItemDTO(
    @Schema(description = "明细ID") Long id,
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "预期数量") Integer expectedQuantity,
    @Schema(description = "已收数量") Integer receivedQuantity,
    @Schema(description = "库位编码") String locationCode
) {}
