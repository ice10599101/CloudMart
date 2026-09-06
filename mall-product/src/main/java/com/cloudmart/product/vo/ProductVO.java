package com.cloudmart.product.vo;

import com.cloudmart.product.dto.SkuDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "商品VO")
public record ProductVO(
    @Schema(description = "商品ID") Long id,
    @Schema(description = "商品名称") String name,
    @Schema(description = "主图") String mainImage,
    @Schema(description = "价格") BigDecimal price,
    @Schema(description = "原价") BigDecimal originalPrice,
    @Schema(description = "库存") Integer stock,
    @Schema(description = "销量") Integer sales,
    @Schema(description = "分类名称") String categoryName,
    @Schema(description = "品牌名称") String brandName,
    @Schema(description = "状态") Integer status,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "SKU 列表（商品详情接口返回；列表接口为 null，价格取 SKU 最低价）") List<SkuDTO> skus
) {}
