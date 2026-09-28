package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建物流请求")
public record CreateShippingRequest(
    @NotNull @Schema(description = "订单ID") Long orderId,
    @NotNull @Schema(description = "仓库ID") Long warehouseId,
    @NotNull @jakarta.validation.constraints.NotBlank(message = "承运商不能为空") @Schema(description = "承运商") String carrier,
    @NotNull @jakarta.validation.constraints.NotBlank(message = "运单号不能为空") @Schema(description = "承运商运单号（真实单号）") String trackingNo,
    @NotNull @Schema(description = "收件人姓名") String receiverName,
    @NotNull @Schema(description = "收件人电话") String receiverPhone,
    @NotNull @Schema(description = "收件人地址") String receiverAddress
) {}
