package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "创建物流请求")
public record CreateShippingRequest(
    @NotNull @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库ID；不传时由 WMS 分配第一个可用仓库") Long warehouseId,
    @NotNull @jakarta.validation.constraints.NotBlank(message = "承运商不能为空") @Schema(description = "承运商") String carrier,
    @NotNull @jakarta.validation.constraints.NotBlank(message = "运单号不能为空") @Schema(description = "承运商运单号（真实单号）") String trackingNo,
    @NotNull @Schema(description = "收件人姓名") String receiverName,
    @NotNull @Schema(description = "收件人电话") String receiverPhone,
    @NotNull @Schema(description = "收件人地址") String receiverAddress
) {}
