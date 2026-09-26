package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 内容举报（B14）：管理员处理审计；PENDING/HANDLED/REJECTED。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_report")
public class PetReport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long reporterUserId;

    /** WALL_MESSAGE / BOTTLE_CONTENT / NICKNAME */
    private String targetType;

    private Long targetId;

    private String reason;

    private String status;

    private Long handledBy;

    private LocalDateTime handledAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
