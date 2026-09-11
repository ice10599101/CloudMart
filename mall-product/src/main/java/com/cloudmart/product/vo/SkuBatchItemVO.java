package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SKU 批量信息项（供秒杀等跨服务 enrich 使用）")
public record SkuBatchItemVO(
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "SKU 图片") String image
) {}
