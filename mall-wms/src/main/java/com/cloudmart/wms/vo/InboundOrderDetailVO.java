package com.cloudmart.wms.vo;

import com.cloudmart.wms.dto.InboundOrderItemDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单详情 VO（TRADE-03）：明细逐项收货依据——
 * itemId/skuId/商品名/expectedQuantity/receivedQuantity/remainingQuantity 由列表 VO 不承载。
 */
@Schema(description = "入库单详情VO")
public record InboundOrderDetailVO(
    @Schema(description = "入库单ID") Long id,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "仓库名称") String warehouseName,
    @Schema(description = "类型 PURCHASE/RETURN/TRANSFER") String type,
    @Schema(description = "关联单号") String referenceNo,
    @Schema(description = "状态 PENDING/RECEIVING/COMPLETED") String status,
    @Schema(description = "计划总数量") Integer totalQuantity,
    @Schema(description = "已收数量") Integer receivedQuantity,
    @Schema(description = "操作员ID") Long operatorUserId,
    @Schema(description = "完成时间") LocalDateTime completedTime,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "入库明细（remainingQuantity = expected - received）")
    List<InboundOrderItemDTO> items
) {}
