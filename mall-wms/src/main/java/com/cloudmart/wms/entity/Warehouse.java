package com.cloudmart.wms.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("warehouses")
public class Warehouse {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String address;

    private String contactPhone;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime deletedAt;
}
