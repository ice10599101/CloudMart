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
 * 宠物币调账（W04）：申请→另一管理员审批→原子入账；审批人取认证上下文，同人不能审批。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_adjustment")
public class PetWalletAdjustment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 目标用户 ID */
    private Long userId;

    /** 调账金额（带符号，禁止 0） */
    private Long delta;

    /** 调账原因 */
    private String reason;

    /** 关联工单号 */
    private String ticketNo;

    /** 申请人（管理员 ID） */
    private Long requestedBy;

    /** 审批人（管理员 ID） */
    private Long approvedBy;

    /** 状态：PENDING/APPROVED/REJECTED */
    private String status;

    /** 乐观锁 */
    private Long version;

    /** 入账流水 ID */
    private Long transactionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime reviewedAt;

    /** R18 审批意见（拒绝必填/通过可填；审计可见） */
    private String reviewReason;
}