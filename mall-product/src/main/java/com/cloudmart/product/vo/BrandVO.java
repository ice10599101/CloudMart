package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "品牌VO")
public record BrandVO(
    @Schema(description = "品牌ID") Long id,
    @Schema(description = "品牌名称") String name,
    @Schema(description = "品牌Logo") String logo,
    @Schema(description = "品牌描述") String description,
    @Schema(description = "状态") Integer status
) {}
