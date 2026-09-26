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
 * 职业任职记录（B10）：一段独立任职历史。
 *
 * <p>每段保留入职/结束时间、结束原因与该段累计收益；晋升/转职只关闭当前段、新开一段，
 * 历史不清除。晋升条件按"当前开放段"的次数判断；ended_at 为空即开放段
 * （uk_career_stint_open 保证每宠物每职业至多一段开放）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_career_stint")
public class PetCareerStint {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long petId;

    private Long userId;

    private String careerCode;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    /** PROMOTED/LEFT */
    private String endReason;

    private Integer workCount;

    private Integer totalCurrency;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
