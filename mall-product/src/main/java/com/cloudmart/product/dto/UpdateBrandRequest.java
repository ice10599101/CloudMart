package com.cloudmart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新品牌请求")
public record UpdateBrandRequest(
    @Schema(description = "品牌名称") String name,
    @Schema(description = "品牌Logo URL") String logo,
    @Schema(description = "品牌描述") String description,
    @Schema(description = "排序值") Integer sortOrder,
    @Schema(description = "状态") Integer status
) {}
