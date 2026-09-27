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
 * 奖励领取事实（W01）：uk(user,bizType,bizId,rewardSlot)——同一业务事实至多领取一次，重放返回同一结果。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_reward_claim")
public class PetRewardClaim {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 绑定宠物 ID（领奖冻结归属） */
    private Long petId;

    /** 业务类型 */
    private String bizType;

    /** 业务实例 ID */
    private String bizId;

    /** 奖励槽位（默认 MAIN） */
    private String rewardSlot;

    /** 冻结奖励快照 JSON */
    private String rewardSnapshot;

    /** 结算钱包域 */
    private String walletDomain;

    /** 状态：PROCESSING/COMPLETED/REJECTED */
    private String status;

    /** 最终结果快照 JSON */
    private String resultJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 终态时间（UTC） */
    private LocalDateTime completedAt;
}