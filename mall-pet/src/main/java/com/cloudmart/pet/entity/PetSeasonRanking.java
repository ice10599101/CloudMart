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

/** 赛季最终榜快照（F2）：结算时落库，历史名次查询依据。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season_ranking")
public class PetSeasonRanking {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long seasonId;

    private Long petId;

    private Long userId;

    /** 最终名次（1 基） */
    private Integer rankNo;

    private Integer level;

    private Integer exp;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
