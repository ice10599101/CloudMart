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
 * 业务操作记录（B01）：一笔不可回滚的远程星光交易对应一条记录。
 *
 * <p>生命周期：本地业务事务内先以 REQUIRES_NEW 提交 PENDING（操作键被占住），
 * 才允许发起远程扣款/发款；远程返回明确成功→COMPLETED，明确失败→FAILED，
 * 超时/宕机/网络中断→UNKNOWN（结果未知），由恢复任务按原 operationId 查询钱包或
 * 幂等重试，禁止换单号二次交易。钱包侧已成功但本地永久无法履约时走补偿单退款
 * （COMPENSATING→COMPENSATED，退款操作键 = 本单 ":refund"）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_operation")
public class PetOperation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationId;

    private Long userId;

    private Long petId;

    /** SHOP_BUY/EVOLVE/CAREER_PROMOTE/CLAIM_WORK/QUEST_CLAIM/BATTLE_REWARD/BOTTLE_REWARD/EVENT_CLAIM/COMPENSATION */
    private String bizType;

    private Long bizRefId;

    /** EARN/SPEND */
    private String direction;

    private Integer amount;

    private String requestDigest;

    /** 奖励/商品快照 JSON（完成前确定的不可变结果，重放依据） */
    private String rewardSnapshot;

    /** PENDING/COMPLETED/FAILED/UNKNOWN/COMPENSATING/COMPENSATED */
    private String status;

    private Integer retryCount;

    private LocalDateTime nextRetryAt;

    /** 钱包返回结果 JSON（credited/balanceAfter/duplicate） */
    private String walletResult;

    private String lastError;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt;
}
