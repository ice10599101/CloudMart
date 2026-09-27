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
 * 宠物币流水（W01）：一笔成功收支一条，不可变；uk(user,bizType,bizKey) 保证同一业务事实至多一笔。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wallet_transaction")
public class PetWalletTransaction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作键（pw_:摘要，幂等入口） */
    private String operationId;

    /** 用户 ID */
    private Long userId;

    /** 宠物 ID（可空） */
    private Long petId;

    /** 币种 */
    private String currency;

    /** 业务类型（§5.3 业务键表） */
    private String bizType;

    /** 业务唯一键 */
    private String bizKey;

    /** 方向：EARN/REFUND/SPEND/ADJUSTMENT */
    private String direction;

    /** 金额（正整数） */
    private Long amount;

    /** 事务状态：COMMITTED/REJECTED */
    private String status;

    /** 请求规范摘要 SHA-256 */
    private String requestHash;

    /** 原交易 ID（退款关联） */
    private Long originalTransactionId;

    /** 规则/配置版本快照 */
    private String ruleVersion;

    /** 不可变结果快照 JSON */
    private String resultJson;

    /** REJECTED 时的领域错误码 */
    private String errorCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 终态时间（UTC） */
    private LocalDateTime completedAt;
}