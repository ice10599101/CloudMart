package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "SKU VO")
public record SkuVO(
    @Schema(description = "SKU ID") Long id,
    @Schema(description = "SKU编码") String skuCode,
    @Schema(description = "属性") String attributes,
    @Schema(description = "价格") BigDecimal price,
    @Schema(description = "库存") Integer stock,
    @Schema(description = "状态") Integer status,
    @Schema(description = "图片列表") List<String> images
) {}
