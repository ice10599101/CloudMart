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
 * 对账差异明细（W04）：人工处置（OPEN/RESOLVED），禁止用当前余额覆盖流水。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_reconcile_item")
public class PetWalletReconcileItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次 ID */
    private Long runId;

    /** 账户 ID */
    private Long accountId;

    /** 期望余额（=SUM(delta) 上界内） */
    private Long expectedBalance;

    /** 实际余额 */
    private Long actualBalance;

    /** 对账时账户版本上界 */
    private Long lastVersion;

    /** 差异（actual-expected） */
    private Long diff;

    /** 状态：OPEN/RESOLVED */
    private String status;

    /** 处置结论（调查说明或关联补偿单号，§8.2 resolve 必填） */
    private String resolutionNote;

    /** 处置管理员（mall-admin 认证主体） */
    private Long resolvedBy;

    /** 处置时间(UTC) */
    private LocalDateTime resolvedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}