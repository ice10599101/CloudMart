package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("shipping_orders")
public class ShippingOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long warehouseId;

    private String shippingNo;

    private String carrier;

    /** 承运商运单号（真实单号，建档必填，WMS-01） */
    private String trackingNo;

    private String status;

    /** 出库时间（WMS-01） */
    private java.time.LocalDateTime shippedAt;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
