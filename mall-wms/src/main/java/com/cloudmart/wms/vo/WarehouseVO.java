package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "仓库VO")
public record WarehouseVO(
    @Schema(description = "仓库ID") Long id,
    @Schema(description = "仓库名称") String name,
    @Schema(description = "仓库编码") String code,
    @Schema(description = "仓库地址") String address,
    @Schema(description = "联系电话") String contactPhone,
    @Schema(description = "状态") Integer status
) {}
