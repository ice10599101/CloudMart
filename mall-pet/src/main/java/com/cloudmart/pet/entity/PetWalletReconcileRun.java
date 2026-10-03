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
 * 钱包对账批次（W04/§5.5）：固定版本上界快照对账，差异落 item 告警不自动改平。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_reconcile_run")
public class PetWalletReconcileRun {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 对账截止时间 */
    private LocalDateTime cutoff;

    /** R19 本轮账户上界（创建时快照 MAX(id)；该轮只扫 <= 此值） */
    private Long maxAccountId;

    /** R19 已扫描到的账户 ID 游标（keyset 推进，中断后续跑不重扫不漏扫） */
    private Long cursorAccountId;

    /** 状态：RUNNING/COMPLETED/FAILED */
    private String status;

    /** 扫描账户数 */
    private Integer accountCount;

    /** 差异账户数 */
    private Integer diffCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime startedAt;
}