package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "入库单VO")
public record InboundOrderVO(
    @Schema(description = "入库单ID") Long id,
    @Schema(description = "仓库名称") String warehouseName,
    @Schema(description = "供应商名称") String supplierName,
    @Schema(description = "状态") String status,
    @Schema(description = "总数量") Integer totalQuantity,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
