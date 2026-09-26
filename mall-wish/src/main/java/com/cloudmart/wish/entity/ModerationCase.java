package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 统一治理工单（N01）：同一内容同一版本仅一个活动 case（uk_case_active 生成列）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_moderation_case")
public class ModerationCase {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String targetType;

    private Long targetId;

    private Long targetRevision;

    /** OPEN / IN_REVIEW / RESOLVED */
    private String status;

    private Long assigneeId;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;
}
