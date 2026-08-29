package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 品牌入驻（Sprint 3.6：审核状态机 PENDING→APPROVED/REJECTED）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_brand")
public class Brand {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String logo;

    private String description;

    /** 认领心愿分类 */
    private Long categoryId;

    /** PENDING/APPROVED/REJECTED */
    private String status;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
