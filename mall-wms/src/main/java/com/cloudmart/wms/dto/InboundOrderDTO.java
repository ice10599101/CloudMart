package com.cloudmart.wms.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "入库单DTO")
public record InboundOrderDTO(
    @Schema(description = "入库单ID") Long id,
    @Schema(description = "仓库ID") Long warehouseId,
    @Schema(description = "类型") String type,
    @Schema(description = "关联单号") String referenceNo,
    @Schema(description = "状态") String status,
    @Schema(description = "总数量") Integer totalQuantity,
    @Schema(description = "已收数量") Integer receivedQuantity,
    @Schema(description = "操作员ID") Long operatorUserId,
    @Schema(description = "完成时间") LocalDateTime completedTime,
    @Schema(description = "备注") String remark,
    @Schema(description = "创建时间") LocalDateTime createdAt,
    @Schema(description = "入库明细") List<InboundOrderItemDTO> items
) {}
