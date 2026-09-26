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

/** 治理决定（N01）：追加写审计，禁止 UPDATE 历史决定。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_moderation_decision")
public class ModerationDecision {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long caseId;

    private Long actorId;

    /** NO_ACTION / HIDE / RESTORE */
    private String decision;

    private String reasonCode;

    private String reasonText;

    private String beforeState;

    private String afterState;

    private Long targetRevision;

    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
