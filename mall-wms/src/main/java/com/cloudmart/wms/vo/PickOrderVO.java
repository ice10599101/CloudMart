package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "拣货单VO")
public record PickOrderVO(
    @Schema(description = "拣货单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库名称") String warehouseName,
    @Schema(description = "状态") String status,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
