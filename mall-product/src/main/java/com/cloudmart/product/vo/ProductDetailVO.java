package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "商品详情VO")
public record ProductDetailVO(
    @Schema(description = "商品ID") Long id,
    @Schema(description = "商品名称") String name,
    @Schema(description = "描述") String description,
    @Schema(description = "主图") String mainImage,
    @Schema(description = "图片列表") List<String> images,
    @Schema(description = "价格") BigDecimal price,
    @Schema(description = "原价") BigDecimal originalPrice,
    @Schema(description = "销量") Integer sales,
    @Schema(description = "分类名称") String categoryName,
    @Schema(description = "品牌名称") String brandName,
    @Schema(description = "状态") Integer status,
    @Schema(description = "SKU列表") List<SkuVO> skus,
    @Schema(description = "评价列表") List<ReviewVO> reviews,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
