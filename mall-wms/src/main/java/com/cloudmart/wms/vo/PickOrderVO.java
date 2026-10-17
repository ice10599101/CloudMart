package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 拣货单列表 VO（TRADE-03：列表与详情分离）。
 * <p>不存在 pickerName/warehouseCode/totalItems/pickedItems 假字段——
 * 拣货员只有 assignedUserId（详情含行项数量与完成数量）。</p>
 */
@Schema(description = "拣货单列表VO")
public record PickOrderVO(
    @Schema(description = "拣货单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "仓库名称") String warehouseName,
    @Schema(description = "状态") String status,
    @Schema(description = "分配拣货员ID") Long assignedUserId,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
