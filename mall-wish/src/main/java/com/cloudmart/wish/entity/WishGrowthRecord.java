package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.GrowthRecordType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("wish_growth_record")
public class WishGrowthRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long wishId;

    private Long userId;

    private GrowthRecordType type;

    private String content;

    private String mediaUrls;

    private Short progressDelta;

    private AuditStatus auditStatus;

    private Boolean isVisible;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
