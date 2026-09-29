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

/** 赛季奖励梯度（F2）：rank_min ≤ 最终名次 ≤ rank_max 命中该档奖励。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season_reward")
public class PetSeasonReward {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long seasonId;

    /** 名次下界（含，1 基） */
    private Integer rankMin;

    /** 名次上界（含） */
    private Integer rankMax;

    private Integer rewardStarlight;

    private Integer rewardExp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
