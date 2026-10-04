package com.cloudmart.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("inventory_logs")
public class InventoryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long skuId;

    private String type;

    private Integer quantity;

    private Long orderId;

    /** T19：来源收货流水 ID（RESTOCK 入账幂等键，uk 兜底重复入账） */
    private String receiptId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
