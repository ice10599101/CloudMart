package com.cloudmart.wms.vo;

import com.cloudmart.wms.dto.PickOrderItemDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 拣货单详情 VO（TRADE-03）：行项数量/完成数量 + 分配拣货员（assignedUserId）。
 */
@Schema(description = "拣货单详情VO")
public record PickOrderDetailVO(
    @Schema(description = "拣货单ID") Long id,
    @Schema(description = "订单ID") Long orderId,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "仓库名称") String warehouseName,
    @Schema(description = "状态 PENDING/PICKING/PICKED/PACKED") String status,
    @Schema(description = "分配拣货员ID") Long assignedUserId,
    @Schema(description = "开始拣货时间") LocalDateTime pickTime,
    @Schema(description = "打包完成时间") LocalDateTime packedTime,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "拣货明细（行项数量/已拣数量）")
    List<PickOrderItemDTO> items
) {}
