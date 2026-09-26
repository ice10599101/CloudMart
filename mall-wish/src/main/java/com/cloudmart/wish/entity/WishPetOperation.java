package com.cloudmart.wish.entity;

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
 * 宠物星光交易幂等记录（B01）。
 *
 * <p>以 {@code operationId} 为业务操作唯一键：去重行与余额更新/流水写入同一事务提交，
 * 因此"该操作已生效"与"余额已变化"原子成立。仅成功结果落库——失败事务整体回滚，
 * 行不存在即表示结果未知，调用方可安全按原单重试（原单号重入命中本表即返回原结果）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_pet_operation")
public class WishPetOperation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String operationId;

    private Long userId;

    /** 操作类型：EARN / SPEND */
    private String operationType;

    private Integer amount;

    /** 实际入账量：EARN 封顶截断后可能小于 amount；SPEND 等于 amount */
    private Integer creditedAmount;

    private Integer balanceAfter;

    private String source;

    private Long refId;

    /** 请求摘要（用户+类型+金额+来源+refId），冲突判定依据 */
    private String requestDigest;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
