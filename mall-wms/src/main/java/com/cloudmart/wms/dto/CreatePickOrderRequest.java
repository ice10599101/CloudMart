package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建拣货单请求")
public record CreatePickOrderRequest(
    @Schema(description = "订单ID") @NotNull Long orderId,
    @Schema(description = "仓库ID") @NotNull Long warehouseId,
    @Schema(description = "备注") String remark
) {}
