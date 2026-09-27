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
 * 宠物币账本（W01）：与流水一一对应；balance_after=balance_before+delta 由 CHECK 与应用层双重保证。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_ledger")
public class PetWalletLedger {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 流水 ID */
    private Long transactionId;

    /** 账户 ID */
    private Long accountId;

    /** 用户 ID */
    private Long userId;

    /** 宠物 ID（可空） */
    private Long petId;

    /** 带符号变动（EARN/REFUND+，SPEND-） */
    private Long delta;

    /** 变动前余额 */
    private Long balanceBefore;

    /** 变动后余额 */
    private Long balanceAfter;

    /** 账户版本（=变动后 version） */
    private Long accountVersion;

    /** 发生时间（UTC） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime occurredAt;
}