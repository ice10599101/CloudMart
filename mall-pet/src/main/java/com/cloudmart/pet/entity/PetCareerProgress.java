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
 * 宠物职业进度（每宠物每职业一行，{@code uk_pet_career_progress} 幂等）。
 *
 * <p>{@code workCount} 是晋升资格的唯一依据；{@code promotedAt} 非空表示已离开该职业，
 * 历史记录保留（换线后可回看每个职业做过多少次）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_career_progress")
public class PetCareerProgress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宠物 ID */
    private Long petId;

    /** 用户 ID（查询冗余） */
    private Long userId;

    /** 职业编码（pet_career_config.code） */
    private String careerCode;

    /** 本职业累计工作次数 */
    private Integer workCount;

    /** 本职业累计星光收入 */
    private Integer totalCurrency;

    /** 入职时间（UTC） */
    private LocalDateTime startedAt;

    /** 晋升离开本职业时间（UTC，NULL = 在职） */
    private LocalDateTime promotedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
