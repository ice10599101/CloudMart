package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建物流请求")
public record CreateShippingRequest(
    @NotNull @Schema(description = "订单ID") Long orderId,
    @NotNull @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "承运商") String carrier,
    @NotNull @Schema(description = "收件人姓名") String receiverName,
    @NotNull @Schema(description = "收件人电话") String receiverPhone,
    @NotNull @Schema(description = "收件人地址") String receiverAddress
) {}
