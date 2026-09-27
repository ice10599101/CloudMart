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
 * 钱包切换批次（W04/§6.5）：清单化切换，期初 0，无脚本改余额。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_cutover_batch")
public class PetWalletCutoverBatch {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次号 */
    private String batchNo;

    /** 切换政策版本 */
    private String policyVersion;

    /** 截止水位 */
    private LocalDateTime cutoff;

    /** 状态：DRAFT/RUNNING/COMPLETED/BLOCKED */
    private String status;

    /** 旧单源水位 */
    private Long sourceWatermark;

    /** 清单校验摘要 */
    private String manifestHash;

    /** 分类统计 JSON */
    private String counts;

    /** 执行人 */
    private Long operator;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime finishedAt;
}