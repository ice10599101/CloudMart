package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "更新仓库请求")
public record UpdateWarehouseRequest(
    @Schema(description = "仓库名称") String name,
    @Schema(description = "仓库地址") String address,
    @Schema(description = "联系电话") String contactPhone,
    @Schema(description = "状态: 0=正常, 1=禁用") Integer status
) {}
