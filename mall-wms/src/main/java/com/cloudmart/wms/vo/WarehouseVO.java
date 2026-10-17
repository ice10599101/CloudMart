package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 仓库 VO（TRADE-03：与实体列对齐——无 code/contactName 列，不凭空展示）。
 */
@Schema(description = "仓库VO")
public record WarehouseVO(
    @Schema(description = "仓库ID") Long id,
    @Schema(description = "仓库名称") String name,
    @Schema(description = "仓库地址") String address,
    @Schema(description = "联系电话") String contactPhone,
    @Schema(description = "状态: 1=正常 0=停用") Integer status,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
