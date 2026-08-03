package com.cloudmart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建品牌请求")
public record CreateBrandRequest(
    @Schema(description = "品牌名称") String name,
    @Schema(description = "品牌Logo URL") String logo,
    @Schema(description = "品牌描述") String description,
    @Schema(description = "排序值") Integer sortOrder
) {}
