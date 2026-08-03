package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "创建入库单请求")
public record CreateInboundOrderRequest(
    @Schema(description = "仓库ID") @NotNull Long warehouseId,
    @Schema(description = "类型: PURCHASE/RETURN/TRANSFER") @NotBlank String type,
    @Schema(description = "关联单号") String referenceNo,
    @Schema(description = "备注") String remark,
    @Schema(description = "入库明细") @NotNull List<InboundItemRequest> items
) {}
