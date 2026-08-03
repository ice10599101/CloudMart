package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("inbound_orders")
public class InboundOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;
    private String type;
    private String referenceNo;
    private String status;
    private Integer totalQuantity;
    private Integer receivedQuantity;
    private Long operatorUserId;
    private LocalDateTime completedTime;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
