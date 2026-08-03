package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("pick_orders")
public class PickOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;
    private Long warehouseId;
    private String status;
    private Long assignedUserId;
    private LocalDateTime pickTime;
    private LocalDateTime packedTime;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
