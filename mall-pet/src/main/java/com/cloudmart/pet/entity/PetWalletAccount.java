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
 * 宠物币账户（W01）：按用户建账，多宠共享；期初 0，不自动迁移历史社区余额。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_account")
public class PetWalletAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 币种代码（PET_COIN） */
    private String currency;

    /** 余额（整数宠物币） */
    private Long balance;

    /** 账户状态：ACTIVE/FROZEN */
    private String status;

    /** 乐观锁版本（与账本 account_version 对齐） */
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}