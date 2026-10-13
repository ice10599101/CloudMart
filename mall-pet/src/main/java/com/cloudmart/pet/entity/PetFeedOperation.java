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
 * 物品喂食已结算操作台账（R02/V79）：{@code (user_id, operation_id)} 唯一，
 * 同一喂食操作键全库只结算一次——双端并发由主宠行 FOR UPDATE 串行化后命中重放分支，
 * 切宠后的重放经此锚定不投新宠；同键不同食物视为幂等冲突。
 *
 * <p>仅记录成功结算（背包已扣、数值已应用）；业务拒绝不落账，状态变化后同键可重试。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_feed_operation")
public class PetFeedOperation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String operationId;

    private Long petId;

    private String itemCode;

    /** 本次占用的双倍喂食权益 ID；普通喂食为 null */
    private Long entitlementId;

    private Boolean doubled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
