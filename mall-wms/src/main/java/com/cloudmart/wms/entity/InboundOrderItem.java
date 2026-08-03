package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("inbound_order_items")
public class InboundOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long inboundOrderId;
    private Long skuId;
    private String productName;
    private Integer expectedQuantity;
    private Integer receivedQuantity;
    private String locationCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
