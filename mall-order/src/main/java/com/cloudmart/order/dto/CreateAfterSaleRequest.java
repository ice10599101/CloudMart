package com.cloudmart.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** T11：售后申请请求体（用户侧）。 */
@Schema(description = "售后申请")
public record CreateAfterSaleRequest(
    @NotNull @Schema(description = "订单ID") Long orderId,
    @Schema(description = "订单项ID（null=整单）") Long itemId,
    @Schema(description = "类型：REFUND_ONLY / RETURN_REFUND") @NotBlank String type,
    @NotBlank @Schema(description = "申请原因") String reason,
    @Schema(description = "附件文件ID（S01 资产，JSON 数组字符串）") String attachmentFileIds,
    @Schema(description = "售后数量（整单不传）") Integer quantity
) {}
