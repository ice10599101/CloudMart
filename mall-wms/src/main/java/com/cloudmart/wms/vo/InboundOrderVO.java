package com.cloudmart.wms.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 入库单列表 VO（TRADE-03：列表与详情分离）。
 * <p>状态契约 PENDING→RECEIVING→COMPLETED；不存在供应商字段（实体无此列，原 VO 是假字段）。</p>
 */
@Schema(description = "入库单列表VO")
public record InboundOrderVO(
    @Schema(description = "入库单ID") Long id,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "仓库名称") String warehouseName,
    @Schema(description = "关联单号（采购/退货来源单）") String referenceNo,
    @Schema(description = "状态") String status,
    @Schema(description = "计划总数量") Integer totalQuantity,
    @Schema(description = "已收数量") Integer receivedQuantity,
    @Schema(description = "操作员ID") Long operatorUserId,
    @Schema(description = "完成时间") LocalDateTime completedTime,
    @Schema(description = "创建时间") LocalDateTime createdAt
) {}
