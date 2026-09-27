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
 * 切换批次用户清单（W04）：逐用户核验；未决单阻断；期初恒 0，不复制社区余额。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_cutover_item")
public class PetWalletCutoverItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次 ID */
    private Long batchId;

    /** 用户 ID */
    private Long userId;

    /** 切换时社区余额快照（仅留档） */
    private Long legacyBalanceSnapshot;

    /** 新钱包期初余额（恒 0） */
    private Long newOpeningBalance;

    /** 旧未决单数（>0 阻断） */
    private Integer legacyPendingCount;

    /** 状态：PENDING/VERIFIED/FAILED */
    private String status;

    /** 核验失败原因 */
    private String error;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}