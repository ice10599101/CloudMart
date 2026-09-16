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
 * 连续签到里程碑领取记录（签到页「连续签到额外奖励」手动领取）。
 *
 * <p>{@code uk_signin_milestone}（user_id + milestone_days）保证每个里程碑
 * 单用户仅可领取一次（并发重复点击由唯一键兜底，映射为 409 已领取）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_signin_milestone_claim")
public class WishSigninMilestoneClaim {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 里程碑连续天数（7/14/30） */
    private Integer milestoneDays;

    /** 本次领取发放的星光数（记录快照，便于对账） */
    private Integer starlightReward;

    /** 本次领取发放的经验数（记录快照，便于对账） */
    private Integer expReward;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}