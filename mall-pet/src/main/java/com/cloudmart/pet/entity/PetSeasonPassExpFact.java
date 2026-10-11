package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 通行证经验事实（R05）：按 (season,user,source,sourceBizId) 去重——
 * 同一任务/宝箱事实不重复计入；跨期按事实归属赛季。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_season_pass_exp_fact")
public class PetSeasonPassExpFact {

    public static final String SOURCE_QUEST = "QUEST";
    public static final String SOURCE_CHEST = "CHEST";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seasonId;

    private Long userId;

    private String source;

    private String sourceBizId;

    private Integer exp;

    private LocalDate businessDate;

    private String ruleVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
