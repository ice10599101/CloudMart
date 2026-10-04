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

    /** 补充说明（1~1000 字符，可选，§7.2） */
    private String description;

    /** 举报自然日（每日配额统计/审计） */
    private java.time.LocalDate reportDate;

    private String status;

    private Long handledBy;

    private LocalDateTime handledAt;

    /** 处理动作：CONTENT_REMOVED/USER_WARNED/USER_PET_BANNED/DISMISSED（P0-2） */
    private String handleAction;

    /** 处理说明（通知举报人的依据，P0-2） */
    private String handleReason;

    /** 是否系统自动举报：1 自动（如危机词命中）0 用户提交（P0-1） */
    private Integer isAuto;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
