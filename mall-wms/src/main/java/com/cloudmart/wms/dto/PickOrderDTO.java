package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "拣货单DTO")
public record PickOrderDTO(
    @Schema(description = "拣货单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "状态") String status,
    @Schema(description = "分配拣货员ID") Long assignedUserId,
    @Schema(description = "开始拣货时间") LocalDateTime pickTime,
    @Schema(description = "打包完成时间") LocalDateTime packedTime,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "拣货明细") List<PickOrderItemDTO> items
) {}
