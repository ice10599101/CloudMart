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

/**
 * 宠物域用户处罚（R05/§9.1）：封禁只限宠物模块新写入；
 * uk(source_report_id, action) 幂等——同一举报同一动作只生成一条处罚。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_user_sanction")
public class PetUserSanction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 被处置用户 ID */
    private Long userId;

    /** 处罚范围：SOCIAL_MUTE / PUBLIC_CONTENT_DISABLED / WARN */
    private String scope;

    /** ACTIVE / EXPIRED / REVOKED */
    private String status;

    /** 生效时间（UTC） */
    private LocalDateTime startsAt;

    /** 到期时间（UTC；NULL=需人工解除） */
    private LocalDateTime expiresAt;

    /** 来源举报 ID（可空：运营主动处置） */
    private Long sourceReportId;

    /** 触发动作 */
    private String action;

    /** 处罚理由（必填） */
    private String reason;

    /** 操作管理员 */
    private Long operatorId;

    /** 撤销管理员 */
    private Long revokedBy;

    /** 撤销理由 */
    private String revokedReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
