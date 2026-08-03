package com.cloudmart.product.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "分类VO")
public record CategoryVO(
    @Schema(description = "分类ID") Long id,
    @Schema(description = "分类名称") String name,
    @Schema(description = "父分类ID") Long parentId,
    @Schema(description = "排序") Integer sortOrder,
    @Schema(description = "图标") String icon,
    @Schema(description = "状态") Integer status
) {}
