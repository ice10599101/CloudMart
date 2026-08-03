package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("pick_order_items")
public class PickOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pickOrderId;
    private Long skuId;
    private String productName;
    private String skuAttributes;
    private Integer quantity;
    private String locationCode;
    private Integer pickedQuantity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
