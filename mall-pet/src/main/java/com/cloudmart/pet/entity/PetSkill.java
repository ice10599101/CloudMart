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
 * 宠物已学技能（{@code uk_pet_skill} 幂等：同一技能只学一次，重复学习返回已学）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_skill")
public class PetSkill {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long petId;

    private Long userId;

    /** 技能编码（pet_skill_config.code） */
    private String skillCode;

    /** 是否已装配 */
    private Boolean equipped;

    private LocalDateTime learnedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
