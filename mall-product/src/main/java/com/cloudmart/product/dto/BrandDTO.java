package com.cloudmart.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "品牌DTO")
public record BrandDTO(
    @Schema(description = "品牌ID") Long id,
    @Schema(description = "品牌名称") String name,
    @Schema(description = "品牌Logo URL") String logo,
    @Schema(description = "品牌描述") String description,
    @Schema(description = "排序值") Integer sortOrder,
    @Schema(description = "状态") Integer status,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
