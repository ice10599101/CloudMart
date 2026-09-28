package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SKU 批量信息项（供秒杀等跨服务 enrich 使用）")
public record SkuBatchItemVO(
    @Schema(description = "SKU ID") Long skuId,
    @Schema(description = "商品ID") Long productId,
    @Schema(description = "商品名称") String productName,
    @Schema(description = "SKU 图片") String image,
    @Schema(description = "权威售价（TRADE-01 服务端报价取此价格）") java.math.BigDecimal price,
    @Schema(description = "销售状态：1 上架 / 0 下架") Integer status,
    @Schema(description = "SKU 属性") String attributes
) {
    /** 旧三参构造（既有调用方兼容） */
    public SkuBatchItemVO(Long skuId, Long productId, String productName, String image) {
        this(skuId, productId, productName, image, null, null, null);
    }
}
