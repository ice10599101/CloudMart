package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "创建仓库请求")
public record CreateWarehouseRequest(
    @Schema(description = "仓库名称") @NotBlank String name,
    @Schema(description = "仓库地址") String address,
    @Schema(description = "联系电话") String contactPhone,
    @Schema(description = "状态: 0=正常, 1=禁用") Integer status
) {}
